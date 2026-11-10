package com.sky.service;

import com.sky.cache.MenuPageCache;
import com.sky.dto.OrderSubmitDTO;
import com.sky.entity.Shop;
import com.sky.entity.User;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.exception.ServiceUnavailableException;
import com.sky.mapper.AddressBookRepository;
import com.sky.mapper.OrderRepository;
import com.sky.mapper.ShopMapper;
import com.sky.mapper.ShoppingCartRepository;
import com.sky.mapper.UserMapper;
import com.sky.vo.AddressVO;
import com.sky.vo.CartLineVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Profile("!bootstrap")
public class UserOrderService {
    private static final Pattern REQUEST_KEY = Pattern.compile("[A-Za-z0-9_-]{16,64}");
    private static final Pattern PHONE = Pattern.compile("1[0-9]{10}");
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");

    private final UserTransactionLock users;
    private final UserMapper userMapper;
    private final ShopMapper shops;
    private final AddressBookRepository addresses;
    private final ShoppingCartRepository carts;
    private final CartCatalogValidator catalog;
    private final OrderRepository orders;
    private final MenuPageCache menuCache;
    private final Clock clock;
    private final PaymentChannel paymentChannel;

    public UserOrderService(UserTransactionLock users, UserMapper userMapper, ShopMapper shops,
            AddressBookRepository addresses, ShoppingCartRepository carts, CartCatalogValidator catalog,
            OrderRepository orders, MenuPageCache menuCache, Clock clock,
            @Value("${sky.payment.channel:MOCK}") PaymentChannel paymentChannel) {
        this.users = users;
        this.userMapper = userMapper;
        this.shops = shops;
        this.addresses = addresses;
        this.carts = carts;
        this.catalog = catalog;
        this.orders = orders;
        this.menuCache = menuCache;
        this.clock = clock;
        this.paymentChannel = paymentChannel;
    }

    @Transactional
    public OrderVO submit(String key, OrderSubmitDTO dto) {
        if (key == null || !REQUEST_KEY.matcher(key).matches()) {
            throw new BadRequestException("Idempotency-Key必须为16至64位字母、数字、下划线或连字符");
        }
        boolean delivery = "DELIVERY".equals(dto.getFulfillment());
        if (delivery && dto.getAddressId() == null) throw new BadRequestException("配送订单需要收餐地址");
        if (!delivery && dto.getAddressId() != null) throw new BadRequestException("自提订单不需要配送地址");

        long userId = users.lockCurrentUser(true);
        String remark = dto.getRemark().trim();
        String hash = requestHash(dto, remark);
        var previous = orders.replay(userId, key, hash);
        if (previous.isPresent()) return previous.get();
        if (paymentChannel == PaymentChannel.WECHAT) {
            throw new ServiceUnavailableException("微信支付暂不可用，暂不能创建订单");
        }

        Shop shop = shops.selectForOrder(1L);
        if (shop == null || !Integer.valueOf(1).equals(shop.getStatus())) {
            throw new ConflictException("食堂当前休息中，暂不能下单");
        }

        User user = userMapper.selectById(userId);
        Recipient recipient = delivery
                ? deliveryRecipient(userId, dto.getAddressId())
                : pickupRecipient(user, shop);

        Map<Long, Integer> lines = new TreeMap<>();
        for (CartLineVO line : carts.list(userId)) lines.put(line.itemId(), line.quantity());
        if (lines.isEmpty()) throw new ConflictException("购物车为空");
        if (lines.size() > 100 || lines.entrySet().stream().anyMatch(
                line -> line.getKey() < 2 || line.getValue() < 1 || line.getValue() > 99)) {
            throw new ConflictException("购物车数据不合法，请清理后重试");
        }

        var allocation = catalog.prepareOrder(lines);
        List<OrderVO.Line> detail = new ArrayList<>();
        BigDecimal amount = BigDecimal.ZERO;
        for (var item : lines.entrySet()) {
            var product = allocation.products().get(item.getKey());
            String kind = ShoppingCartRepository.isDish(item.getKey()) ? "dish" : "setmeal";
            detail.add(new OrderVO.Line(item.getKey(), product.name(), kind, product.price(), item.getValue()));
            amount = amount.add(product.price().multiply(BigDecimal.valueOf(item.getValue())));
        }
        if (amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new ConflictException("订单金额超出允许范围");
        }

        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        String number = "YW" + UUID.randomUUID().toString().replace("-", "");
        var events = List.of(new OrderVO.Event("UNPAID", now, "创建订单，预占库存"));
        OrderVO draft = new OrderVO(0, number, userId, user.getName(), "UNPAID", "UNPAID",
                dto.getFulfillment(), amount, now, null, now.plusMinutes(15), recipient.name(),
                recipient.phone(), recipient.address(), remark, detail, events, paymentChannel.name());

        orders.deductStock(allocation.stock());
        long id = orders.insert(draft, dto.getAddressId(), allocation.stock());
        for (OrderVO.Line line : detail) {
            orders.insertLine(id, line, allocation.products().get(line.itemId()).image());
        }

        OrderVO result = orders.findOwned(userId, id).orElseThrow();
        carts.clear(userId);
        orders.saveReplay(userId, key, hash, result);
        menuCache.invalidateAfterCommit();
        return result;
    }

    @Transactional(readOnly = true)
    public OrderVO detail(long id) {
        if (id < 1 || id > 9007199254740991L) throw new BadRequestException("订单ID不合法");
        return orders.findOwned(users.currentUserId(), id)
                .orElseThrow(() -> new NotFoundException("订单不存在"));
    }

    private Recipient deliveryRecipient(long userId, long addressId) {
        AddressVO address = addresses.find(userId, addressId)
                .orElseThrow(() -> new NotFoundException("收餐地址不存在"));
        if (address.phone() == null || !PHONE.matcher(address.phone()).matches()
                || address.consignee() == null || address.consignee().isBlank()
                || address.building() == null || address.building().isBlank()
                || address.detail() == null || address.detail().isBlank()) {
            throw new BadRequestException("收餐地址不完整，请先修正地址");
        }
        return new Recipient(address.consignee(), address.phone(), address.building() + " " + address.detail());
    }

    private Recipient pickupRecipient(User user, Shop shop) {
        if (user.getPhone() == null || !PHONE.matcher(user.getPhone()).matches()) {
            throw new BadRequestException("自提需要有效联系电话，请先补充资料或选择配送");
        }
        if (shop.getPickupPoint() == null || shop.getPickupPoint().isBlank()) {
            throw new ConflictException("食堂未配置自提点，请选择配送");
        }
        return new Recipient(user.getName(), user.getPhone(), shop.getPickupPoint());
    }

    private String requestHash(OrderSubmitDTO dto, String remark) {
        String normalized = dto.getFulfillment() + "|" + dto.getAddressId() + "|" + remark;
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256不可用", exception);
        }
    }

    private record Recipient(String name, String phone, String address) {}
}
