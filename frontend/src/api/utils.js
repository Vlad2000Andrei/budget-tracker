export const PRESET_ICONS = {
  fastfood: '🍔',
  shopping_cart: '🛒',
  restaurant: '🍕',
  power: '⚡',
  work: '💼',
  payments: '💵',
  savings: '🏦',
  flight: '✈️',
  directions_car: '🚗',
  movie: '🎬',
  medical_services: '🏥',
  school: '🎓',
  fitness_center: '💪',
  home: '🏠',
  pets: '🐶',
  gift: '🎁',
  water: '💧',
  wifi: '📶',
  local_gas_station: '⛽',
  sports_esports: '🎮',
  checkroom: '👕',
  spa: '💆',
};

export function getCategoryIcon(icon) {
  return PRESET_ICONS[icon] || icon || '📦';
}
