package com.sky.service;

import com.sky.dto.CartRequest;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.exception.NotFoundException;
import com.sky.mapper.CartReplayRepository;
import com.sky.mapper.ShoppingCartRepository;
import com.sky.vo.CartLineVO;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Service
@Profile("!bootstrap")
public class ShoppingCartService {
    private static final long MAX_ITEM_ID = 9007199254740991L;
    private static final Pattern REQUEST_KEY = Pattern.compile("[A-Za-z0-9_-]{16,64}");

    private final UserTransactionLock users;
    private final ShoppingCartRepository carts;
    private final CartCatalogValidator catalog;
    private final CartReplayRepository replay;

    public ShoppingCartService(UserTransactionLock users, ShoppingCartRepository carts,
            CartCatalogValidator catalog, CartReplayRepository replay) {
        this.users = users;
        this.carts = carts;
        this.catalog = catalog;
        this.replay = replay;
    }

    public List<CartLineVO> list() {
        return carts.list(users.currentUserId());
    }

    @Transactional
    public List<CartLineVO> add(String key, long itemId, int quantity) {
        validateItemId(itemId);
        if (key == null || !REQUEST_KEY.matcher(key).matches()) {
            throw new BadRequestException("Idempotency-Key必须为16至64位字母、数字、下划线或连字符");
        }

        long userId = users.lockCurrentUser(true);
        var previous = replay.find(userId, key, itemId, quantity);
        if (previous.isPresent()) return previous.get();

        Map<Long, Integer> lines = currentLines(userId);
        Integer before = lines.get(itemId);
        int target = lines.getOrDefault(itemId, 0) + quantity;
        if (target > 99) throw new ConflictException("单个商品最多99份");
        lines.put(itemId, target);
        if (lines.size() > 100) throw new ConflictException("购物车最多容纳100种商品");

        var products = catalog.validate(lines);
        if (before == null) carts.insert(userId, itemId, target, products.get(itemId));
        else carts.updateQuantity(userId, itemId, target);

        List<CartLineVO> result = carts.list(userId);
        replay.save(userId, key, itemId, quantity, result);
        return result;
    }

    @Transactional
    public List<CartLineVO> replace(List<CartRequest.Item> items) {
        Map<Long, Integer> lines = new TreeMap<>();
        for (CartRequest.Item item : items) {
            validateItemId(item.getItemId());
            if (lines.putIfAbsent(item.getItemId(), item.getQuantity()) != null) {
                throw new BadRequestException("购物车商品不能重复");
            }
        }
        long userId = users.lockCurrentUser(false);
        var products = catalog.validate(lines);
        carts.clear(userId);
        for (var line : lines.entrySet()) {
            carts.insert(userId, line.getKey(), line.getValue(), products.get(line.getKey()));
        }
        return carts.list(userId);
    }

    @Transactional
    public List<CartLineVO> setQuantity(long itemId, int quantity) {
        validateItemId(itemId);
        long userId = users.lockCurrentUser(false);
        Map<Long, Integer> lines = currentLines(userId);
        Integer before = lines.get(itemId);
        if (before == null) throw new NotFoundException("购物车商品不存在");

        if (quantity > before) {
            lines.put(itemId, quantity);
            catalog.validate(lines);
        }
        if (quantity != before) carts.updateQuantity(userId, itemId, quantity);
        return carts.list(userId);
    }

    @Transactional
    public List<CartLineVO> remove(long itemId) {
        validateItemId(itemId);
        long userId = users.lockCurrentUser(false);
        carts.remove(userId, itemId);
        return carts.list(userId);
    }

    @Transactional
    public List<CartLineVO> clear() {
        carts.clear(users.lockCurrentUser(false));
        return List.of();
    }

    private Map<Long, Integer> currentLines(long userId) {
        Map<Long, Integer> lines = new TreeMap<>();
        for (CartLineVO line : carts.list(userId)) lines.put(line.itemId(), line.quantity());
        return lines;
    }

    private void validateItemId(long id) {
        if (id < 2 || id > MAX_ITEM_ID) throw new BadRequestException("商品ID不合法");
    }
}
