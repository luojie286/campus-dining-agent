package com.sky.service;

import com.sky.dto.OrderPageQueryDTO;
import com.sky.exception.BadRequestException;
import com.sky.exception.ConflictException;
import com.sky.mapper.ReportRepository;
import com.sky.vo.DashboardVO;
import com.sky.vo.OrderReportVO;
import com.sky.vo.PeriodVO;
import com.sky.vo.RevenueReportVO;
import com.sky.vo.SalesItemVO;
import com.sky.vo.SalesReportVO;
import com.sky.vo.ShopVO;
import com.sky.vo.UserReportVO;
import com.sky.vo.WorkbenchVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一统计口径：所有报表、工作台、导出和后续 Agent 都调用这里，
 * 不各自写一套 SQL。范围按 Asia/Shanghai 业务日的半开区间 [begin 00:00, end 次日 00:00)。
 */
@Service
@Profile("!bootstrap")
public class ReportService {

    public static final int MAX_RANGE_DAYS = 90;
    public static final int MAX_EXPORT_DAYS = 31;
    public static final int MAX_EXPORT_ROWS = 10000;

    private static final int DEFAULT_TOP_N = 10;
    private static final int SALES_ROW_LIMIT = 50000;
    private static final String MIXED_NOTE = "收入与支付订单按付款时间，创建订单/状态/完成率按创建时间，销量按有效成交明细；";

    /** 一次查询复用的范围：from/to 是数据库参数，dates 用于趋势缺日补 0。 */
    public record Range(LocalDate begin, LocalDate end, LocalDateTime from,
                        LocalDateTime to, List<LocalDate> dates) {
        public PeriodVO period() {
            return new PeriodVO(begin, end);
        }
    }

    private final EmployeePermissionService permissions;
    private final ReportRepository reports;
    private final ShopService shopService;
    private final Clock clock;
    private final PaymentChannel paymentChannel;

    public ReportService(EmployeePermissionService permissions, ReportRepository reports,
            ShopService shopService, Clock clock,
            @Value("${sky.payment.channel:MOCK}") PaymentChannel paymentChannel) {
        this.permissions = permissions;
        this.reports = reports;
        this.shopService = shopService;
        this.clock = clock;
        this.paymentChannel = paymentChannel;
    }

    @Transactional(readOnly = true)
    public RevenueReportVO revenue(String begin, String end) {
        permissions.requireOperationsAccess();
        Range range = range(begin, end, MAX_RANGE_DAYS, false);

        BigDecimal gross = scale(reports.sumPaidAmount(range.from(), range.to()));
        BigDecimal refund = scale(reports.sumRefundAmount(range.from(), range.to()));
        Map<LocalDate, ReportRepository.DailyAmount> paid = dailyAmounts(
                reports.dailyPaid(range.from(), range.to()));
        Map<LocalDate, BigDecimal> refunds = refundAmounts(range,
                reports.dailyRefund(range.from(), range.to()));

        List<RevenueReportVO.Trend> trend = new ArrayList<>();
        for (LocalDate date : range.dates()) {
            ReportRepository.DailyAmount day = paid.get(date);
            BigDecimal grossDay = day == null ? BigDecimal.ZERO : scale(day.amount());
            BigDecimal refundDay = refunds.getOrDefault(date, BigDecimal.ZERO);
            trend.add(new RevenueReportVO.Trend(date, grossDay, refundDay,
                    grossDay.subtract(refundDay), day == null ? 0 : day.count()));
        }

        return new RevenueReportVO(range.period(), gross, refund, gross.subtract(refund),
                reports.countPaidOrders(range.from(), range.to()), trend,
                note("营业额按支付成功时间，退款按退款成功时间；netCashAmount 不是利润，可能为负"));
    }

    @Transactional(readOnly = true)
    public UserReportVO users(String begin, String end) {
        permissions.requireOperationsAccess();
        Range range = range(begin, end, MAX_RANGE_DAYS, false);

        int newUsers = reports.countNewUsers(range.from(), range.to());
        int totalUsers = reports.countUsersBefore(range.to());
        int orderingUsers = reports.countOrderingUsers(range.from(), range.to());
        int payingUsers = reports.countPayingUsers(range.from(), range.to());

        Map<LocalDate, Integer> dailyNew = dailyCounts(range, reports.dailyNewUsers(range.from(), range.to()));
        Map<LocalDate, Integer> dailyOrdering = dailyCounts(range,
                reports.dailyOrderingUsers(range.from(), range.to()));
        Map<LocalDate, Integer> dailyPaying = dailyCounts(range,
                reports.dailyPayingUsers(range.from(), range.to()));

        int cumulative = reports.countUsersBefore(range.from());
        List<UserReportVO.Trend> trend = new ArrayList<>();
        for (LocalDate date : range.dates()) {
            cumulative += dailyNew.getOrDefault(date, 0);
            trend.add(new UserReportVO.Trend(date, dailyNew.getOrDefault(date, 0), cumulative,
                    dailyOrdering.getOrDefault(date, 0), dailyPaying.getOrDefault(date, 0)));
        }

        return new UserReportVO(range.period(), newUsers, totalUsers, orderingUsers, payingUsers,
                trend, note("区间人数为去重结果；totalUsers 是截至结束日的累计注册数，逐日值是截至该日累计"));
    }

    @Transactional(readOnly = true)
    public OrderReportVO orders(String begin, String end) {
        permissions.requireOperationsAccess();
        Range range = range(begin, end, MAX_RANGE_DAYS, false);

        int created = reports.countCreatedOrders(range.from(), range.to());
        int paid = reports.countPaidOrders(range.from(), range.to());
        int valid = reports.countValidOrders(range.from(), range.to());
        int completed = reports.countCompletedOrders(range.from(), range.to());
        int cancelled = reports.countCancelledOrders(range.from(), range.to());
        BigDecimal completionRate = percentage(completed, created);

        Map<LocalDate, Integer> dailyCreated = dailyCounts(range,
                reports.dailyCreatedOrders(range.from(), range.to()));
        Map<LocalDate, Integer> dailyPaid = dailyCounts(range,
                reports.dailyPaidOrders(range.from(), range.to()));
        Map<LocalDate, Integer> dailyValid = dailyCounts(range,
                reports.dailyValidOrders(range.from(), range.to()));
        Map<LocalDate, Integer> dailyCompleted = dailyCounts(range,
                reports.dailyCompletedOrders(range.from(), range.to()));
        Map<LocalDate, Integer> dailyCancelled = dailyCounts(range,
                reports.dailyCancelledOrders(range.from(), range.to()));

        List<OrderReportVO.Trend> trend = new ArrayList<>();
        for (LocalDate date : range.dates()) {
            trend.add(new OrderReportVO.Trend(date,
                    dailyCreated.getOrDefault(date, 0), dailyPaid.getOrDefault(date, 0),
                    dailyValid.getOrDefault(date, 0), dailyCompleted.getOrDefault(date, 0),
                    dailyCancelled.getOrDefault(date, 0)));
        }

        return new OrderReportVO(range.period(), created, paid, valid, completed, cancelled,
                completionRate, statusNames(reports.statusCounts(range.from(), range.to())),
                trend, note("创建/状态/完成率按创建时间，paidOrders 按付款时间，是两套口径"));
    }

    @Transactional(readOnly = true)
    public SalesReportVO sales(String begin, String end, String kind, Integer topN) {
        permissions.requireOperationsAccess();
        Range range = range(begin, end, MAX_RANGE_DAYS, false);
        int limit = normalizeTopN(topN);
        validateKind(kind);

        return new SalesReportVO(range.period(), salesItems(range, kind, limit),
                note("按付款时间的有效成交明细汇总；套餐按套餐计，不重复计入组成菜品"));
    }

    @Transactional(readOnly = true)
    public DashboardVO overview(String begin, String end) {
        permissions.requireOperationsAccess();
        Range range = range(begin, end, MAX_RANGE_DAYS, false);

        BigDecimal revenue = scale(reports.sumPaidAmount(range.from(), range.to()));
        int paidOrders = reports.countPaidOrders(range.from(), range.to());
        int createdOrders = reports.countCreatedOrders(range.from(), range.to());
        int completedOrders = reports.countCompletedOrders(range.from(), range.to());
        int cancelledOrders = reports.countCancelledOrders(range.from(), range.to());

        Map<LocalDate, ReportRepository.DailyAmount> paid = dailyAmounts(
                reports.dailyPaid(range.from(), range.to()));
        List<DashboardVO.TrendPoint> trend = new ArrayList<>();
        for (LocalDate date : range.dates()) {
            ReportRepository.DailyAmount day = paid.get(date);
            trend.add(new DashboardVO.TrendPoint(date,
                    day == null ? BigDecimal.ZERO : scale(day.amount()),
                    day == null ? 0 : day.count()));
        }

        return new DashboardVO(revenue, paidOrders, average(revenue, paidOrders),
                completedOrders, percentage(cancelledOrders, createdOrders), createdOrders,
                statusNames(reports.statusCounts(range.from(), range.to())), trend,
                salesItems(range, null, DEFAULT_TOP_N), range.period(),
                note(MIXED_NOTE + "趋势按付款时间，状态与完成率按创建时间"));
    }

    @Transactional(readOnly = true)
    public WorkbenchVO workbench(String date) {
        permissions.requireOperationsAccess();
        LocalDate businessDate = businessDate(date);
        LocalDateTime from = businessDate.atStartOfDay();
        LocalDateTime to = businessDate.plusDays(1).atStartOfDay();

        BigDecimal revenue = scale(reports.sumPaidAmount(from, to));
        int paidOrders = reports.countPaidOrders(from, to);
        ShopVO shop = shopService.getForAdmin();

        return new WorkbenchVO(businessDate, shop, revenue, paidOrders,
                reports.countCreatedOrders(from, to),
                reports.countCompletedOrders(from, to),
                average(revenue, paidOrders),
                statusNames(reports.pendingCounts()),
                reports.dishCounts(), reports.setmealCounts(),
                note("当日收入与支付按付款时间；创建/完成按当日创建订单的当前状态；"
                        + "待办统计全部日期未完结订单，商品数量为当前原始上下架状态"));
    }

    /** 导出用的订单行；走同一 Service 与同一范围校验，避免导出和页面两套口径。 */
    @Transactional(readOnly = true)
    public List<ReportRepository.ExportOrderRow> exportOrderRows(Range range) {
        permissions.requireOperationsAccess();
        List<ReportRepository.ExportOrderRow> rows = reports.exportOrders(
                range.from(), range.to(), MAX_EXPORT_ROWS + 1);
        if (rows.size() > MAX_EXPORT_ROWS) {
            throw new ConflictException("导出数据超过 10000 行，请缩小日期范围");
        }
        return rows;
    }

    public Range exportRange(String begin, String end) {
        permissions.requireOperationsAccess();
        return range(begin, end, MAX_EXPORT_DAYS, true);
    }

    /** 范围型统计缺省最近 7 自然日；只传一端、倒序、超长范围都是参数错误。 */
    public Range range(String begin, String end, int maxDays, boolean required) {
        LocalDate today = LocalDate.now(clock);
        LocalDate first;
        LocalDate last;

        if (isBlank(begin) && isBlank(end)) {
            if (required) throw new BadRequestException("导出必须填写开始日期和结束日期");
            last = today;
            first = today.minusDays(6);
        } else if (isBlank(begin) || isBlank(end)) {
            throw new BadRequestException("开始日期和结束日期必须同时填写");
        } else {
            first = parseDate(begin);
            last = parseDate(end);
        }

        if (first.isAfter(last)) throw new BadRequestException("开始日期不能晚于结束日期");
        long days = ChronoUnit.DAYS.between(first, last) + 1;
        if (days > maxDays) throw new BadRequestException("统计范围不能超过 " + maxDays + " 天");

        List<LocalDate> dates = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            dates.add(first.plusDays(offset));
        }
        return new Range(first, last, first.atStartOfDay(), last.plusDays(1).atStartOfDay(), dates);
    }

    /** 销量聚合在 Java 里完成，便于明确"名称取最近快照"和排序规则。 */
    public List<SalesItemVO> salesItems(Range range, String kind, int topN) {
        validateKind(kind);
        List<ReportRepository.SalesRow> rows = reports.salesRows(
                range.from(), range.to(), kind, SALES_ROW_LIMIT);
        if (rows.size() >= SALES_ROW_LIMIT) {
            throw new ConflictException("统计明细过多，请缩小日期范围");
        }

        Map<Long, Accumulator> grouped = new LinkedHashMap<>();
        for (ReportRepository.SalesRow row : rows) {
            Accumulator item = grouped.computeIfAbsent(row.itemId(), ignored -> new Accumulator());
            item.quantity += row.quantity();
            item.amount = item.amount.add(row.price().multiply(BigDecimal.valueOf(row.quantity())));
            // salesRows 已按付款时间、明细 id 升序，最后一次写入就是最近一条入选快照的名称。
            item.name = row.name();
        }

        List<SalesItemVO> items = new ArrayList<>();
        for (Map.Entry<Long, Accumulator> entry : grouped.entrySet()) {
            Accumulator item = entry.getValue();
            items.add(new SalesItemVO(entry.getKey(), item.name, item.quantity, scale(item.amount)));
        }
        items.sort(Comparator.comparingInt(SalesItemVO::quantity).reversed()
                .thenComparingLong(SalesItemVO::id));
        return items.size() > topN ? new ArrayList<>(items.subList(0, topN)) : items;
    }

    private static class Accumulator {
        private String name = "";
        private int quantity;
        private BigDecimal amount = BigDecimal.ZERO;
    }

    private LocalDate businessDate(String value) {
        LocalDate today = LocalDate.now(clock);
        if (isBlank(value)) return today;
        LocalDate date = parseDate(value);
        if (date.isAfter(today)) throw new BadRequestException("工作台日期不能是未来日期");
        return date;
    }

    private LocalDate parseDate(String value) {
        try {
            LocalDate date = LocalDate.parse(value);
            if (date.getYear() < 1000) throw new BadRequestException("统计日期超出范围");
            return date;
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BadRequestException("请输入真实日期，格式为YYYY-MM-DD");
        }
    }

    private void validateKind(String kind) {
        if (kind != null && !kind.isBlank()
                && !"dish".equals(kind) && !"setmeal".equals(kind)) {
            throw new BadRequestException("商品类型只能是 dish 或 setmeal");
        }
    }

    private int normalizeTopN(Integer topN) {
        if (topN == null) return DEFAULT_TOP_N;
        if (topN < 1 || topN > 100) throw new BadRequestException("topN 必须在 1 到 100 之间");
        return topN;
    }

    private Map<LocalDate, Integer> dailyCounts(Range range, List<ReportRepository.DailyCount> rows) {
        Map<LocalDate, Integer> values = new LinkedHashMap<>();
        for (LocalDate date : range.dates()) values.put(date, 0);
        for (ReportRepository.DailyCount row : rows) values.put(row.date(), row.value());
        return values;
    }

    private Map<LocalDate, ReportRepository.DailyAmount> dailyAmounts(
            List<ReportRepository.DailyAmount> rows) {
        Map<LocalDate, ReportRepository.DailyAmount> values = new LinkedHashMap<>();
        for (ReportRepository.DailyAmount row : rows) values.put(row.date(), row);
        return values;
    }

    private Map<LocalDate, BigDecimal> refundAmounts(
            Range range, List<ReportRepository.DailyAmount> rows) {
        Map<LocalDate, BigDecimal> values = new LinkedHashMap<>();
        for (LocalDate date : range.dates()) values.put(date, BigDecimal.ZERO);
        for (ReportRepository.DailyAmount row : rows) values.put(row.date(), scale(row.amount()));
        return values;
    }

    private Map<String, Integer> statusNames(Map<Integer, Integer> raw) {
        Map<String, Integer> names = new LinkedHashMap<>();
        for (OrderPageQueryDTO.State state : OrderPageQueryDTO.State.values()) {
            names.put(state.name(), raw.getOrDefault(state.code(), 0));
        }
        return names;
    }

    private BigDecimal percentage(int part, int total) {
        if (total == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(part).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal average(BigDecimal revenue, int paidOrders) {
        if (paidOrders == 0) return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return revenue.divide(BigDecimal.valueOf(paidOrders), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private String note(String definition) {
        String channel = paymentChannel == PaymentChannel.MOCK
                ? "当前为模拟支付口径，不产生真实扣款与真实收款"
                : "当前按实际支付渠道统计";
        return definition + "；" + channel + "。报表是当前数据库视图，不是不可变财务快照。";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
