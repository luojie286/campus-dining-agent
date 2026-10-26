/** 转义分隔符，并阻止可编辑商品名被电子表格解释成公式。 */
export function downloadCsv(filename: string, rows: (string | number)[][]): void {
  const escape = (value: string | number) => {
    let text = String(value)
    if (/^[=+\-@\t\r]/.test(text)) text = "'" + text
    return '"' + text.replaceAll('"', '""') + '"'
  }
  const blob = new Blob(['\uFEFF' + rows.map(row => row.map(escape).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8;' })
  const url = URL.createObjectURL(blob); const anchor = document.createElement('a'); anchor.href = url; anchor.download = filename; anchor.click(); setTimeout(() => URL.revokeObjectURL(url), 1000)
}
