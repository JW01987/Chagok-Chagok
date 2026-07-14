import dayjs from 'dayjs';

export function formatCurrency(amount: number): string {
  return new Intl.NumberFormat('ko-KR', {
    style: 'currency',
    currency: 'KRW',
    maximumFractionDigits: 0,
  }).format(amount);
}

export function formatDate(date: string | Date, pattern = 'YYYY.MM.DD'): string {
  return dayjs(date).format(pattern);
}
