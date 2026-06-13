export function getCategoryIcon(icon) {
  const ICON_MAP = {
    fastfood: '🍔',
    shopping_cart: '🛒',
    restaurant: '🍕',
    power: '⚡',
    work: '💼',
    payments: '💵',
    savings: '🏦',
  };
  return ICON_MAP[icon] || icon || '📦';
}
