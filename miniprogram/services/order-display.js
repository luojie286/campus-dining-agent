const labels = {
  UNPAID: '待支付', PREPARING: '制作中', READY_DELIVERY: '待配送',
  DELIVERING: '配送中', READY_PICKUP: '待自提', COMPLETED: '已完成', CANCELLED: '已取消'
}

function businessTime(time) {
  return time ? time.replace('T', ' ').slice(0, 16) : ''
}

function displayOrder(order) {
  return {
    ...order,
    statusLabel: labels[order.status] || order.status,
    createdLabel: businessTime(order.createdAt),
    deadlineLabel: businessTime(order.paymentDeadline),
    amountLabel: Number(order.amount).toFixed(2),
    events: order.events.map((event, index) => ({
      ...event,
      key: index,
      label: labels[event.status] || event.status,
      timeLabel: businessTime(event.time)
    }))
  }
}

module.exports = { labels, displayOrder }
