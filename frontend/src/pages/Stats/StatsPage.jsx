import { useState, useEffect, useMemo, useCallback } from 'react';
import axiosInstance from '../../api/axiosInstance';
import { getCategoryIcon } from '../../api/utils';
import { useAuth } from '../../context/AuthContext';
import {
  ResponsiveContainer,
  AreaChart,
  Area,
  LineChart,
  Line,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  Tooltip,
  Legend,
} from 'recharts';
import styles from './StatsPage.module.css';

// Formats amount with currency format
function fmt(amount, currency = 'USD') {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(amount);
}

// Formats tick values compactly for mobile chart space optimization
function formatCompact(value) {
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    compactDisplay: 'short',
  }).format(value);
}

// Visual category icon retriever
// Visual category icon retriever
function getIcon(icon) {
  return getCategoryIcon(icon);
}

// Mutes saturated hex colors dynamically for elegant pastel visualizations (max 35% saturation)
function muteColor(hex) {
  if (!hex || !hex.startsWith('#')) return '#BEC9C7';
  let r = parseInt(hex.substring(1, 3), 16);
  let g = parseInt(hex.substring(3, 5), 16);
  let b = parseInt(hex.substring(5, 7), 16);

  r /= 255; g /= 255; b /= 255;
  const max = Math.max(r, g, b), min = Math.min(r, g, b);
  let h, s, l = (max + min) / 2;

  if (max === min) {
    h = s = 0;
  } else {
    const d = max - min;
    s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
    switch (max) {
      case r: h = (g - b) / d + (g < b ? 6 : 0); break;
      case g: h = (b - r) / d + 2; break;
      case b: h = (r - g) / d + 4; break;
      default: break;
    }
    h /= 6;
  }

  // Soft desaturation limits color intensity
  s = Math.min(s, 0.35);
  const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
  l = isDark ? Math.max(0.35, Math.min(l, 0.55)) : Math.max(0.55, Math.min(l, 0.75));

  const hue2rgb = (p, q, t) => {
    if (t < 0) t += 1;
    if (t > 1) t -= 1;
    if (t < 1/6) return p + (q - p) * 6 * t;
    if (t < 1/2) return q;
    if (t < 2/3) return p + (q - p) * (2/3 - t) * 6;
    return p;
  };

  const q = l < 0.5 ? l * (1 + s) : l + s - l * s;
  const p = 2 * l - q;
  r = Math.round(hue2rgb(p, q, h + 1/3) * 255);
  g = Math.round(hue2rgb(p, q, h) * 255);
  b = Math.round(hue2rgb(p, q, h - 1/3) * 255);

  const toHex = (x) => {
    const hexStr = x.toString(16);
    return hexStr.length === 1 ? '0' + hexStr : hexStr;
  };

  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

export default function StatsPage() {
  const { user } = useAuth();
  const defaultCurrency = user?.defaultCurrency || 'USD';

  // Mobile layout states
  const [isMobile, setIsMobile] = useState(false);
  const [isMobileFilterOpen, setIsMobileFilterOpen] = useState(false);

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth <= 992);
    };
    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // API Data
  const [categories, setCategories] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [savingsGoals, setSavingsGoals] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Time Period state
  const [periodType, setPeriodType] = useState('last30'); // 'day', 'last30', 'mtd', 'year', 'ytd', 'custom'
  const [customStart, setCustomStart] = useState('');
  const [customEnd, setCustomEnd] = useState('');

  // UI layout state
  const [metricTab, setMetricTab] = useState('EXPENSE'); // 'EXPENSE', 'INCOME', 'SAVINGS', 'NET_CASH_FLOW', 'BALANCE_EVOLUTION'
  const [chartType, setChartType] = useState('line'); // 'pie', 'bar', 'line'
  const [excludeModalOpen, setExcludeModalOpen] = useState(false);
  const [excludeSearchQuery, setExcludeSearchQuery] = useState('');

  // Preferences: active filters and exclusions loaded from localStorage
  const [preferences, setPreferences] = useState(() => {
    try {
      const saved = localStorage.getItem('budget_tracker_stats_prefs');
      if (saved) {
        const parsed = JSON.parse(saved);
        return {
          selectedAccountIds: parsed.selectedAccountIds || [],
          selectedCategoryIds: parsed.selectedCategoryIds || [],
          excludedTransactionIds: parsed.excludedTransactionIds || [],
          excludedRecurrenceIds: parsed.excludedRecurrenceIds || [],
        };
      }
    } catch (e) {
      console.warn('Failed to parse budget_tracker_stats_prefs', e);
    }
    return {
      selectedAccountIds: [],
      selectedCategoryIds: [],
      excludedTransactionIds: [],
      excludedRecurrenceIds: [],
    };
  });

  // Save preferences to localStorage
  useEffect(() => {
    localStorage.setItem('budget_tracker_stats_prefs', JSON.stringify(preferences));
  }, [preferences]);

  // Compute Active Selected Dates
  const activeDates = useMemo(() => {
    const today = new Date();
    let start = new Date();
    let end = new Date();

    switch (periodType) {
      case 'day':
        start.setHours(0, 0, 0, 0);
        end.setHours(23, 59, 59, 999);
        break;
      case 'last30':
        start = new Date(today);
        start.setDate(today.getDate() - 30);
        start.setHours(0, 0, 0, 0);
        end.setHours(23, 59, 59, 999);
        break;
      case 'mtd':
        start = new Date(today.getFullYear(), today.getMonth(), 1, 0, 0, 0, 0);
        end = new Date(today);
        break;
      case 'year':
        start = new Date(today.getFullYear(), 0, 1, 0, 0, 0, 0);
        end = new Date(today.getFullYear(), 11, 31, 23, 59, 59, 999);
        break;
      case 'ytd':
        start = new Date(today.getFullYear(), 0, 1, 0, 0, 0, 0);
        end = new Date(today);
        break;
      case 'custom':
        if (customStart) {
          const s = new Date(customStart);
          s.setHours(0, 0, 0, 0);
          start = s;
        } else {
          start = new Date(today.getFullYear(), today.getMonth(), 1, 0, 0, 0, 0);
        }
        if (customEnd) {
          const e = new Date(customEnd);
          e.setHours(23, 59, 59, 999);
          end = e;
        } else {
          end = new Date(today);
        }
        break;
      default:
        break;
    }

    // Previous Ranges
    const daysInPeriod = Math.ceil((end - start) / (1000 * 60 * 60 * 24));
    
    // Last Week Shift (7 days ago)
    const lwStart = new Date(start);
    lwStart.setDate(lwStart.getDate() - 7);
    const lwEnd = new Date(end);
    lwEnd.setDate(lwEnd.getDate() - 7);

    // Last Month Shift (1 month ago)
    const lmStart = new Date(start);
    lmStart.setMonth(lmStart.getMonth() - 1);
    const lmEnd = new Date(end);
    lmEnd.setMonth(lmEnd.getMonth() - 1);

    // Maximum bounding date range covering all periods
    const minDate = new Date(Math.min(start, lwStart, lmStart));
    const maxDate = new Date(Math.max(end, lwEnd, lmEnd));

    return {
      current: { start, end },
      lastWeek: { start: lwStart, end: lwEnd },
      lastMonth: { start: lmStart, end: lmEnd },
      bounding: { start: minDate, end: maxDate },
      daysCount: daysInPeriod,
    };
  }, [periodType, customStart, customEnd]);

  // Format Helper
  const formatDateString = (date) => {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${d}`;
  };

  // Fetch API Data
  const loadStatsData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const { start, end } = activeDates.bounding;
      const startStr = formatDateString(start);
      const endStr = formatDateString(end);

      const [catsRes, budgetsRes, goalsRes, summaryRes, txsRes] = await Promise.all([
        axiosInstance.get('/v1/categories'),
        axiosInstance.get('/v1/budgets'),
        axiosInstance.get('/v1/savings-goals'),
        axiosInstance.get('/v1/dashboard-summary'),
        axiosInstance.get(`/v1/transactions?startDate=${startStr}&endDate=${endStr}`),
      ]);

      setCategories(catsRes.data);
      setBudgets(budgetsRes.data);
      setSavingsGoals(goalsRes.data);
      // summaryRes provides current convertedBalances
      setAccounts(summaryRes.data?.accounts || []);
      setTransactions(txsRes.data);

      // Initialize filter preferences if empty
      setPreferences((prev) => {
        const accs = summaryRes.data?.accounts || [];
        const cats = catsRes.data;
        return {
          ...prev,
          selectedAccountIds: prev.selectedAccountIds.length > 0
            ? prev.selectedAccountIds.filter(id => id === null || accs.some(a => a.id === id))
            : [...accs.map(a => a.id), null],
          selectedCategoryIds: prev.selectedCategoryIds.length > 0
            ? prev.selectedCategoryIds.filter(id => cats.some(c => c.id === id))
            : cats.map(c => c.id),
        };
      });
    } catch (err) {
      console.error('Stats page fetch failed', err);
      setError(err.message || 'Failed to load page statistics.');
    } finally {
      setLoading(false);
    }
  }, [activeDates.bounding]);

  useEffect(() => {
    loadStatsData();
  }, [periodType, customStart, customEnd]);

  // Category descendant mapper helper
  const categoryDescendantsMap = useMemo(() => {
    const map = new Map();
    categories.forEach(c => {
      // Find descendants
      const descendants = [c.id];
      let check = [c.id];
      while (check.length > 0) {
        const next = [];
        for (const id of check) {
          const children = categories.filter(child => child.parentId === id).map(child => child.id);
          descendants.push(...children);
          next.push(...children);
        }
        check = next;
      }
      map.set(c.id, descendants);
    });
    return map;
  }, [categories]);

  // Filter & Exclusions processor
  const processedTransactions = useMemo(() => {
    return transactions.filter((tx) => {
      // Excluded transactions
      if (preferences.excludedTransactionIds.includes(tx.id)) return false;
      // Excluded recurrence templates
      if (tx.recurrenceRuleId && preferences.excludedRecurrenceIds.includes(tx.recurrenceRuleId)) return false;

      // Filter by Account (handles MOVE both directions)
      const matchesAccount = preferences.selectedAccountIds.includes(tx.accountId) ||
                            (tx.type === 'MOVE' && (preferences.selectedAccountIds.includes(tx.fromAccountId) ||
                                                    preferences.selectedAccountIds.includes(tx.toAccountId)));
      if (!matchesAccount) return false;

      // Filter by Category
      const matchesCategory = preferences.selectedCategoryIds.includes(tx.categoryId);
      if (!matchesCategory) return false;

      return true;
    });
  }, [transactions, preferences]);

  // Group transactions by date ranges
  const dateSplitTransactions = useMemo(() => {
    const currentTxs = [];
    const lastWeekTxs = [];
    const lastMonthTxs = [];

    const cur = activeDates.current;
    const lw = activeDates.lastWeek;
    const lm = activeDates.lastMonth;

    processedTransactions.forEach((tx) => {
      const txDate = new Date(tx.date);
      if (txDate >= cur.start && txDate <= cur.end) currentTxs.push(tx);
      if (txDate >= lw.start && txDate <= lw.end) lastWeekTxs.push(tx);
      if (txDate >= lm.start && txDate <= lm.end) lastMonthTxs.push(tx);
    });

    return { current: currentTxs, lastWeek: lastWeekTxs, lastMonth: lastMonthTxs };
  }, [processedTransactions, activeDates]);

  // Aggregate Category totals for Cashflow card
  const categoryCashflowBreakdown = useMemo(() => {
    const expenseMap = new Map();
    const incomeMap = new Map();
    const savingsMap = new Map();

    let totalExpenses = 0;
    let totalIncome = 0;
    let totalSavings = 0;

    dateSplitTransactions.current.forEach((tx) => {
      const amt = tx.convertedAmount || 0;
      if (tx.type === 'EXPENSE') {
        expenseMap.set(tx.categoryId, (expenseMap.get(tx.categoryId) || 0) + amt);
        totalExpenses += amt;
      } else if (tx.type === 'INCOME') {
        incomeMap.set(tx.categoryId, (incomeMap.get(tx.categoryId) || 0) + amt);
        totalIncome += amt;
      } else if (tx.type === 'SAVINGS') {
        savingsMap.set(tx.categoryId, (savingsMap.get(tx.categoryId) || 0) + amt);
        totalSavings += amt;
      }
    });

    const formatBreakdown = (map, total) => {
      const list = [];
      map.forEach((sum, catId) => {
        const cat = categories.find(c => c.id === catId);
        if (cat) {
          list.push({
            id: cat.id,
            name: cat.name,
            icon: cat.icon,
            color: muteColor(cat.color || '#BEC9C7'),
            value: sum,
            pct: total > 0 ? (sum / total) * 100 : 0,
            parentId: cat.parentId,
          });
        }
      });

      // Nest subcategories under parent categories
      const parents = list.filter(c => c.parentId === null);
      const children = list.filter(c => c.parentId !== null);

      parents.forEach(p => {
        p.children = children.filter(c => c.parentId === p.id);
        // Include child values in parent if children exist
        p.subTotal = p.value + p.children.reduce((acc, c) => acc + c.value, 0);
      });

      // Add remaining children whose parents had no direct transactions
      children.forEach(c => {
        if (!parents.some(p => p.id === c.parentId)) {
          const parentCat = categories.find(cat => cat.id === c.parentId);
          if (parentCat) {
            parents.push({
              id: parentCat.id,
              name: parentCat.name,
              icon: parentCat.icon,
              color: muteColor(parentCat.color || '#BEC9C7'),
              value: 0,
              subTotal: c.value,
              pct: total > 0 ? (c.value / total) * 100 : 0,
              parentId: null,
              children: [c],
            });
          }
        }
      });

      return parents.sort((a, b) => (b.subTotal || b.value) - (a.subTotal || a.value));
    };

    return {
      EXPENSE: {
        total: totalExpenses,
        categories: formatBreakdown(expenseMap, totalExpenses),
      },
      INCOME: {
        total: totalIncome,
        categories: formatBreakdown(incomeMap, totalIncome),
      },
      SAVINGS: {
        total: totalSavings,
        categories: formatBreakdown(savingsMap, totalSavings),
      },
    };
  }, [dateSplitTransactions.current, categories]);

  // Aggregate KPI metrics and variance calculations
  const kpis = useMemo(() => {
    const calculateStats = (txs) => {
      let income = 0;
      let expenses = 0;
      let savings = 0;

      txs.forEach((tx) => {
        const amt = tx.convertedAmount || 0;
        if (tx.type === 'EXPENSE') expenses += amt;
        else if (tx.type === 'INCOME') income += amt;
        else if (tx.type === 'SAVINGS') savings += amt;
      });

      return { income, expenses, savings, net: income - expenses - savings };
    };

    const currentStats = calculateStats(dateSplitTransactions.current);
    const lastWeekStats = calculateStats(dateSplitTransactions.lastWeek);
    const lastMonthStats = calculateStats(dateSplitTransactions.lastMonth);

    const getVariance = (current, previous) => {
      const diff = current - previous;
      const pct = previous > 0 ? (diff / previous) * 100 : 0;
      return { diff, pct };
    };

    return {
      current: currentStats,
      lastWeek: getVariance(currentStats.net, lastWeekStats.net),
      lastMonth: getVariance(currentStats.net, lastMonthStats.net),
      currentSavingsRate: currentStats.income > 0 ? (currentStats.savings / currentStats.income) * 100 : 0,
      lastMonthSavingsRate: getVariance(
        currentStats.income > 0 ? (currentStats.savings / currentStats.income) * 100 : 0,
        lastMonthStats.income > 0 ? (lastMonthStats.savings / lastMonthStats.income) * 100 : 0
      ),
    };
  }, [dateSplitTransactions]);

  // Chart Data: group current period transactions by day or category
  const chartData = useMemo(() => {
    const isTimelineTab = metricTab === 'NET_CASH_FLOW' || metricTab === 'BALANCE_EVOLUTION';
    
    // Categorized Pie/Bar aggregates
    if (!isTimelineTab && chartType !== 'line') {
      const map = new Map();
      dateSplitTransactions.current
        .filter((tx) => tx.type === metricTab)
        .forEach((tx) => {
          map.set(tx.categoryId, (map.get(tx.categoryId) || 0) + tx.convertedAmount);
        });

      return Array.from(map.entries())
        .map(([catId, value]) => {
          const cat = categories.find(c => c.id === catId);
          return {
            name: cat ? cat.name : 'Unknown',
            value,
            color: muteColor(cat?.color || '#BEC9C7'),
          };
        })
        .sort((a, b) => b.value - a.value);
    }

    // Daily Timeline plots
    const dailyMap = new Map();
    const cur = activeDates.current;
    
    // Fill days
    for (let d = new Date(cur.start); d <= cur.end; d.setDate(d.getDate() + 1)) {
      const dateKey = formatDateString(d);
      dailyMap.set(dateKey, { date: dateKey, EXPENSE: 0, INCOME: 0, SAVINGS: 0, netFlow: 0 });
    }

    // Populate transaction values
    dateSplitTransactions.current.forEach((tx) => {
      const dateKey = formatDateString(new Date(tx.date));
      if (dailyMap.has(dateKey)) {
        const item = dailyMap.get(dateKey);
        const amt = tx.convertedAmount || 0;
        if (tx.type === 'EXPENSE') item.EXPENSE += amt;
        else if (tx.type === 'INCOME') item.INCOME += amt;
        else if (tx.type === 'SAVINGS') item.SAVINGS += amt;
      }
    });

    // Compute Net Flow
    dailyMap.forEach((val) => {
      val.netFlow = val.INCOME - val.EXPENSE - val.SAVINGS;
    });

    const timelineData = Array.from(dailyMap.values()).sort((a, b) => a.date.localeCompare(b.date));

    // For Balance Evolution, calculate cumulative balance
    if (metricTab === 'BALANCE_EVOLUTION') {
      // Find current total balance in default currency of selected accounts (ignoring null)
      const selectedAccounts = accounts.filter(a => preferences.selectedAccountIds.includes(a.id));
      let currentBal = selectedAccounts.reduce((sum, a) => sum + (a.convertedBalance || 0), 0);

      // Collect all processed transactions after the active end date, excluding unassigned ones
      const endBoundary = cur.end;
      const walkBackTxs = processedTransactions.filter(tx => 
        new Date(tx.date) > endBoundary &&
        (tx.accountId !== null || tx.type === 'MOVE')
      );
      
      // Walking backwards from today to the end of the active window
      walkBackTxs.forEach((tx) => {
        const amt = tx.convertedAmount || 0;
        if (tx.type === 'INCOME') {
          if (preferences.selectedAccountIds.includes(tx.accountId)) currentBal -= amt;
        } else if (tx.type === 'EXPENSE') {
          if (preferences.selectedAccountIds.includes(tx.accountId)) currentBal += amt;
        } else if (tx.type === 'SAVINGS') {
          if (preferences.selectedAccountIds.includes(tx.accountId)) {
            const acc = accounts.find(a => a.id === tx.accountId);
            if (acc?.type === 'SAVINGS') currentBal -= amt;
            else currentBal += amt;
          }
        } else if (tx.type === 'MOVE') {
          if (preferences.selectedAccountIds.includes(tx.fromAccountId)) currentBal += amt;
          if (preferences.selectedAccountIds.includes(tx.toAccountId)) currentBal -= amt;
        }
      });

      // Now walk backwards day-by-day within the timeline
      const result = [];
      let runningBal = currentBal;

      for (let i = timelineData.length - 1; i >= 0; i--) {
        const day = timelineData[i];
        // The balance at the end of the day is runningBal
        result.unshift({
          date: day.date,
          balance: runningBal,
        });

        // Walk back over this day's transactions (ignoring unassigned ones)
        const dayTxs = dateSplitTransactions.current.filter(tx => 
          formatDateString(new Date(tx.date)) === day.date &&
          (tx.accountId !== null || tx.type === 'MOVE')
        );
        
        let dayIncome = 0;
        let dayExpense = 0;
        
        dayTxs.forEach(tx => {
          const amt = tx.convertedAmount || 0;
          if (tx.type === 'INCOME') {
            if (preferences.selectedAccountIds.includes(tx.accountId)) dayIncome += amt;
          } else if (tx.type === 'EXPENSE') {
            if (preferences.selectedAccountIds.includes(tx.accountId)) dayExpense += amt;
          } else if (tx.type === 'SAVINGS') {
            if (preferences.selectedAccountIds.includes(tx.accountId)) {
              const acc = accounts.find(a => a.id === tx.accountId);
              if (acc) {
                if (acc.type === 'SAVINGS') {
                  dayIncome += amt;
                } else {
                  dayExpense += amt;
                }
              }
            }
          } else if (tx.type === 'MOVE') {
            if (preferences.selectedAccountIds.includes(tx.fromAccountId)) dayExpense += amt;
            if (preferences.selectedAccountIds.includes(tx.toAccountId)) dayIncome += amt;
          }
        });

        runningBal = runningBal - dayIncome + dayExpense;
      }

      return result;
    }

    return timelineData;
  }, [dateSplitTransactions.current, metricTab, chartType, categories, activeDates, accounts, processedTransactions, preferences.selectedAccountIds]);

  // Aggregate Category breakdown for active budgets & savings goals
  const activeBudgetsAndGoals = useMemo(() => {
    const activeBuds = budgets.filter((b) => {
      const active = !activeDates.current.start.isBefore && 
                     (!b.endDate || new Date(b.endDate) >= activeDates.current.start);
      return active;
    });

    const mappedBuds = activeBuds.map((b) => {
      const cat = b.categoryId
        ? categories.find(c => c.id === b.categoryId)
        : null;

      // Spent sum (using descendants)
      const allowedCategories = b.categoryId
        ? (categoryDescendantsMap.get(b.categoryId) || [b.categoryId])
        : categories.map(c => c.id);

      const spent = dateSplitTransactions.current
        .filter((tx) => tx.type === 'EXPENSE' && allowedCategories.includes(tx.categoryId))
        .reduce((sum, tx) => sum + (tx.convertedAmount || 0), 0);

      const pct = b.amountLimit > 0 ? Math.round((spent / b.amountLimit) * 100) : 0;

      return {
        id: b.id,
        name: cat ? cat.name : 'Overall Budget',
        icon: cat ? cat.icon : '💰',
        color: muteColor(cat ? cat.color : '#4CAF50'),
        spent,
        limit: b.amountLimit,
        pct,
      };
    });

    const mappedGoals = savingsGoals.map((g) => {
      const cat = categories.find(c => c.id === g.categoryId);
      const pct = g.targetAmount > 0 ? Math.round((g.currentAmount / g.targetAmount) * 100) : 0;
      return {
        id: g.id,
        name: cat ? cat.name : 'Savings Goal',
        icon: cat ? cat.icon : '🏦',
        color: muteColor(cat ? cat.color : '#FF9800'),
        current: g.currentAmount,
        target: g.targetAmount,
        pct,
      };
    });

    return { budgets: mappedBuds, goals: mappedGoals };
  }, [budgets, savingsGoals, categories, activeDates.current, dateSplitTransactions.current, categoryDescendantsMap]);

  // Collapsible category filter checkbox action
  const handleToggleCategory = (catId) => {
    setPreferences((prev) => {
      const descendants = categoryDescendantsMap.get(catId) || [catId];
      const isCurrentlySelected = prev.selectedCategoryIds.includes(catId);
      let nextSelected;

      if (isCurrentlySelected) {
        // Deselect category and all descendants
        nextSelected = prev.selectedCategoryIds.filter(id => !descendants.includes(id));
      } else {
        // Select category and all descendants
        nextSelected = [...prev.selectedCategoryIds, ...descendants];
      }

      return { ...prev, selectedCategoryIds: nextSelected };
    });
  };

  // Group categories into parent-child tree
  const categoryTree = useMemo(() => {
    const parents = categories.filter(c => c.parentId === null);
    const children = categories.filter(c => c.parentId !== null);

    return parents.map(p => ({
      ...p,
      children: children.filter(c => c.parentId === p.id),
    }));
  }, [categories]);

  // Modal transactions filter lists
  const modalTransactions = useMemo(() => {
    const query = excludeSearchQuery.toLowerCase().trim();
    const txList = transactions.map(tx => {
      const cat = categories.find(c => c.id === tx.categoryId);
      const acc = accounts.find(a => a.id === tx.accountId);
      return {
        ...tx,
        categoryName: cat ? cat.name : 'Unknown',
        categoryIcon: cat ? cat.icon : '📦',
        accountName: acc ? acc.name : '—',
      };
    });

    if (!query) {
      return txList.sort((a, b) => b.convertedAmount - a.convertedAmount);
    }

    return txList
      .filter(tx => 
        tx.notes?.toLowerCase().includes(query) ||
        tx.categoryName.toLowerCase().includes(query) ||
        tx.convertedAmount.toString().includes(query)
      )
      .sort((a, b) => b.convertedAmount - a.convertedAmount);
  }, [transactions, categories, accounts, excludeSearchQuery]);

  if (loading && categories.length === 0) {
    return (
      <div className={styles.loadingContainer}>
        <div className={styles.spinner} aria-hidden="true" />
        <span>Loading stats & overviews...</span>
      </div>
    );
  }

  if (error) {
    return (
      <div className={styles.errorContainer}>
        <span className={styles.errorIcon}>⚠️</span>
        <p className={styles.errorText}>{error}</p>
        <button onClick={loadStatsData} className={styles.retryBtn}>Retry</button>
      </div>
    );
  }

  return (
    <div className={styles.container}>
      {/* MOBILE FILTER DRAWER BACKDROP */}
      {isMobile && isMobileFilterOpen && (
        <div className={styles.backdrop} onClick={() => setIsMobileFilterOpen(false)} />
      )}

      {/* HEADER SECTION */}
      <header className={styles.header}>
        <div className={styles.headerTitleArea}>
          <span className={styles.headerIcon} role="img" aria-label="Insights">📊</span>
          <div className={styles.headerText}>
            <h1>Stats & Overviews</h1>
            <p>Analyze spending trends, view net cash flow, and manage exclusions.</p>
          </div>
        </div>
        <div className={styles.headerControls}>
          <button
            onClick={() => setIsMobileFilterOpen(true)}
            className={styles.mobileFilterToggleBtn}
            aria-label="Open filters"
            title="Filters & Exclusions"
          >
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="18"
              height="18"
              viewBox="0 0 24 24"
              fill="none"
              stroke="currentColor"
              strokeWidth="2"
              strokeLinecap="round"
              strokeLinejoin="round"
            >
              <polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3" />
            </svg>
            <span>Filters</span>
          </button>

          <select
            value={periodType}
            onChange={(e) => setPeriodType(e.target.value)}
            className={styles.periodSelect}
            aria-label="Select date range preset"
          >
            <option value="day">Today</option>
            <option value="last30">Last 30 Days</option>
            <option value="mtd">Month to Date (MTD)</option>
            <option value="year">Current Year</option>
            <option value="ytd">Year to Date (YTD)</option>
            <option value="custom">Custom Date Range</option>
          </select>
          {periodType === 'custom' && (
            <div className={styles.customDatePickers}>
              <input
                type="date"
                value={customStart}
                onChange={(e) => setCustomStart(e.target.value)}
                className={styles.dateInput}
                aria-label="Start date"
              />
              <span>to</span>
              <input
                type="date"
                value={customEnd}
                onChange={(e) => setCustomEnd(e.target.value)}
                className={styles.dateInput}
                aria-label="End date"
              />
            </div>
          )}
        </div>
      </header>

      <div className={styles.layoutGrid}>
        {/* LEFT FILTERS SIDEBAR */}
        <aside className={`${styles.sidebar} ${isMobileFilterOpen ? styles.mobileOpen : ''}`}>
          <div className={styles.sidebarHeader}>
            <h3>Filters & Exclusions</h3>
            {isMobile && (
              <button
                onClick={() => setIsMobileFilterOpen(false)}
                className={styles.mobileCloseBtn}
                aria-label="Close filters"
              >
                ✕
              </button>
            )}
          </div>

          <div className={styles.sidebarContent}>
              {/* Accounts Checklist */}
              <div className={styles.filterGroup}>
                <h4>Accounts</h4>
                <div className={styles.checkboxList}>
                  {accounts.map(acc => (
                    <label key={acc.id} className={styles.checkboxLabel}>
                      <input
                        type="checkbox"
                        checked={preferences.selectedAccountIds.includes(acc.id)}
                        onChange={(e) => {
                          const checked = e.target.checked;
                          setPreferences(prev => ({
                            ...prev,
                            selectedAccountIds: checked
                              ? [...prev.selectedAccountIds, acc.id]
                              : prev.selectedAccountIds.filter(id => id !== acc.id),
                          }));
                        }}
                      />
                      <span>{acc.name}</span>
                    </label>
                  ))}
                  <label className={styles.checkboxLabel}>
                    <input
                      type="checkbox"
                      checked={preferences.selectedAccountIds.includes(null)}
                      onChange={(e) => {
                        const checked = e.target.checked;
                        setPreferences(prev => ({
                          ...prev,
                          selectedAccountIds: checked
                            ? [...prev.selectedAccountIds, null]
                            : prev.selectedAccountIds.filter(id => id !== null),
                        }));
                      }}
                    />
                    <span style={{ fontStyle: 'italic', opacity: 0.85 }}>Unassigned / No Account</span>
                  </label>
                </div>
              </div>

              {/* Categories Checklist */}
              <div className={styles.filterGroup}>
                <h4>Categories</h4>
                <div className={styles.categoryTreeList}>
                  {categoryTree.map(parent => {
                    const isParentSelected = preferences.selectedCategoryIds.includes(parent.id);
                    return (
                      <div key={parent.id} className={styles.treeNode}>
                        <label className={styles.checkboxLabel}>
                          <input
                            type="checkbox"
                            checked={isParentSelected}
                            onChange={() => handleToggleCategory(parent.id)}
                          />
                          <span className={styles.categoryTitle}>
                            {getIcon(parent.icon)} {parent.name}
                          </span>
                        </label>
                        {parent.children.length > 0 && (
                          <div className={styles.treeChildren}>
                            {parent.children.map(child => (
                              <label key={child.id} className={styles.checkboxLabel}>
                                <input
                                  type="checkbox"
                                  checked={preferences.selectedCategoryIds.includes(child.id)}
                                  onChange={() => {
                                    setPreferences(prev => {
                                      const isSel = prev.selectedCategoryIds.includes(child.id);
                                      return {
                                        ...prev,
                                        selectedCategoryIds: isSel
                                          ? prev.selectedCategoryIds.filter(id => id !== child.id)
                                          : [...prev.selectedCategoryIds, child.id],
                                      };
                                    });
                                  }}
                                />
                                <span>{getIcon(child.icon)} {child.name}</span>
                              </label>
                            ))}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              </div>

              {/* Exclusions checklist */}
              <div className={styles.filterGroup}>
                <h4>Exclusions</h4>
                <div className={styles.exclusionControlButtons}>
                  <button
                    onClick={() => setExcludeModalOpen(true)}
                    className={styles.manageExclusionsBtn}
                  >
                    🔍 Exclude Transactions
                  </button>
                </div>
                {/* Display Excluded Recurrent rules */}
                {preferences.excludedRecurrenceIds.length > 0 && (
                  <div className={styles.excludedListBlock}>
                    <h5>Excluded Recurrent Sets:</h5>
                    <ul className={styles.excludedMiniList}>
                      {preferences.excludedRecurrenceIds.map(ruleId => (
                        <li key={ruleId} className={styles.excludedMiniItem}>
                          <span>🔁 Recurrence Rule #{ruleId}</span>
                          <button
                            onClick={() => {
                              setPreferences(prev => ({
                                ...prev,
                                excludedRecurrenceIds: prev.excludedRecurrenceIds.filter(id => id !== ruleId),
                              }));
                            }}
                            className={styles.removeExclusionBtn}
                            title="Restore rule"
                          >
                            ✕
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                {/* Display Excluded Ad-hoc Transactions */}
                {preferences.excludedTransactionIds.length > 0 && (
                  <div className={styles.excludedListBlock}>
                    <h5>Excluded Ad-hoc Txs ({preferences.excludedTransactionIds.length}):</h5>
                    <ul className={styles.excludedMiniList}>
                      {preferences.excludedTransactionIds.map(txId => {
                        const tx = transactions.find(t => t.id === txId);
                        return (
                          <li key={txId} className={styles.excludedMiniItem}>
                            <span>
                              {tx ? `${getIcon(categories.find(c => c.id === tx.categoryId)?.icon)} ${fmt(tx.convertedAmount, defaultCurrency)}` : `Tx #${txId}`}
                            </span>
                            <button
                              onClick={() => {
                                setPreferences(prev => ({
                                  ...prev,
                                  excludedTransactionIds: prev.excludedTransactionIds.filter(id => id !== txId),
                                }));
                              }}
                              className={styles.removeExclusionBtn}
                              title="Restore transaction"
                            >
                              ✕
                            </button>
                          </li>
                        );
                      })}
                    </ul>
                  </div>
                )}
              </div>
            </div>
        </aside>

        {/* RIGHT ANALYTICS CONTENT AREA */}
        <main className={styles.mainContent}>
          {/* CATEGORY CASHFLOW BREAKDOWN CARD */}
          <section className={styles.cashflowBreakdownCard}>
            <div className={styles.cashflowCardHeader}>
              <h3>Categories</h3>
              <span className={styles.helpBadge}>Current period aggregates</span>
            </div>
            
            <div className={styles.cashflowGrid}>
              {/* Expenses breakdown */}
              <div className={styles.cashflowTypeSection}>
                <h4 className={styles.expenseTypeTitle}>Expenses: {fmt(categoryCashflowBreakdown.EXPENSE.total, defaultCurrency)}</h4>
                {categoryCashflowBreakdown.EXPENSE.categories.length === 0 ? (
                  <p className={styles.emptyBreakdownText}>No expenses in this period.</p>
                ) : (
                  <div className={styles.breakdownRows}>
                    {categoryCashflowBreakdown.EXPENSE.categories.map(cat => (
                      <div key={cat.id} className={styles.breakdownRowGroup}>
                        <div className={styles.breakdownRow}>
                          <span className={styles.breakdownLabel}>
                            {getIcon(cat.icon)} {cat.name}
                          </span>
                          <span className={styles.breakdownValue}>
                            {fmt(cat.subTotal || cat.value, defaultCurrency)} ({cat.pct.toFixed(1)}%)
                          </span>
                        </div>
                        <div className={styles.fillTrack}>
                          <div
                            className={styles.fillBar}
                            style={{ width: `${cat.pct}%`, backgroundColor: cat.color }}
                          />
                        </div>
                        {/* Render Subcategories inline if they exist */}
                        {cat.children && cat.children.length > 0 && (
                          <div className={styles.breakdownChildrenRows}>
                            {cat.children.map(child => (
                              <div key={child.id} className={styles.breakdownChildRowGroup}>
                                <div className={styles.breakdownChildRow}>
                                  <span className={styles.breakdownChildLabel}>
                                    ↳ {getIcon(child.icon)} {child.name}
                                  </span>
                                  <span className={styles.breakdownChildValue}>
                                    {fmt(child.value, defaultCurrency)} ({child.pct.toFixed(1)}%)
                                  </span>
                                </div>
                                <div className={`${styles.fillTrack} ${styles.childTrack}`}>
                                  <div
                                    className={styles.fillBar}
                                    style={{ width: `${child.pct}%`, backgroundColor: child.color }}
                                  />
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Income breakdown */}
              <div className={styles.cashflowTypeSection}>
                <h4 className={styles.incomeTypeTitle}>Income: {fmt(categoryCashflowBreakdown.INCOME.total, defaultCurrency)}</h4>
                {categoryCashflowBreakdown.INCOME.categories.length === 0 ? (
                  <p className={styles.emptyBreakdownText}>No income in this period.</p>
                ) : (
                  <div className={styles.breakdownRows}>
                    {categoryCashflowBreakdown.INCOME.categories.map(cat => (
                      <div key={cat.id} className={styles.breakdownRowGroup}>
                        <div className={styles.breakdownRow}>
                          <span className={styles.breakdownLabel}>
                            {getIcon(cat.icon)} {cat.name}
                          </span>
                          <span className={styles.breakdownValue}>
                            {fmt(cat.subTotal || cat.value, defaultCurrency)} ({cat.pct.toFixed(1)}%)
                          </span>
                        </div>
                        <div className={styles.fillTrack}>
                          <div
                            className={styles.fillBar}
                            style={{ width: `${cat.pct}%`, backgroundColor: cat.color }}
                          />
                        </div>
                        {cat.children && cat.children.length > 0 && (
                          <div className={styles.breakdownChildrenRows}>
                            {cat.children.map(child => (
                              <div key={child.id} className={styles.breakdownChildRowGroup}>
                                <div className={styles.breakdownChildRow}>
                                  <span className={styles.breakdownChildLabel}>
                                    ↳ {getIcon(child.icon)} {child.name}
                                  </span>
                                  <span className={styles.breakdownChildValue}>
                                    {fmt(child.value, defaultCurrency)} ({child.pct.toFixed(1)}%)
                                  </span>
                                </div>
                                <div className={`${styles.fillTrack} ${styles.childTrack}`}>
                                  <div
                                    className={styles.fillBar}
                                    style={{ width: `${child.pct}%`, backgroundColor: child.color }}
                                  />
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>

              {/* Savings breakdown */}
              <div className={styles.cashflowTypeSection}>
                <h4 className={styles.savingsTypeTitle}>Savings: {fmt(categoryCashflowBreakdown.SAVINGS.total, defaultCurrency)}</h4>
                {categoryCashflowBreakdown.SAVINGS.categories.length === 0 ? (
                  <p className={styles.emptyBreakdownText}>No savings in this period.</p>
                ) : (
                  <div className={styles.breakdownRows}>
                    {categoryCashflowBreakdown.SAVINGS.categories.map(cat => (
                      <div key={cat.id} className={styles.breakdownRowGroup}>
                        <div className={styles.breakdownRow}>
                          <span className={styles.breakdownLabel}>
                            {getIcon(cat.icon)} {cat.name}
                          </span>
                          <span className={styles.breakdownValue}>
                            {fmt(cat.subTotal || cat.value, defaultCurrency)} ({cat.pct.toFixed(1)}%)
                          </span>
                        </div>
                        <div className={styles.fillTrack}>
                          <div
                            className={styles.fillBar}
                            style={{ width: `${cat.pct}%`, backgroundColor: cat.color }}
                          />
                        </div>
                        {cat.children && cat.children.length > 0 && (
                          <div className={styles.breakdownChildrenRows}>
                            {cat.children.map(child => (
                              <div key={child.id} className={styles.breakdownChildRowGroup}>
                                <div className={styles.breakdownChildRow}>
                                  <span className={styles.breakdownChildLabel}>
                                    ↳ {getIcon(child.icon)} {child.name}
                                  </span>
                                  <span className={styles.breakdownChildValue}>
                                    {fmt(child.value, defaultCurrency)} ({child.pct.toFixed(1)}%)
                                  </span>
                                </div>
                                <div className={`${styles.fillTrack} ${styles.childTrack}`}>
                                  <div
                                    className={styles.fillBar}
                                    style={{ width: `${child.pct}%`, backgroundColor: child.color }}
                                  />
                                </div>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </section>

          {/* MAIN GRAPH AREA */}
          <section className={styles.card}>
            <div className={styles.chartControlsRow}>
              {/* Metric Type Picker Tabs */}
              <div className={styles.segmentedTabs}>
                <button
                  onClick={() => { setMetricTab('EXPENSE'); setChartType('pie'); }}
                  className={`${styles.segmentBtn} ${metricTab === 'EXPENSE' ? styles.segmentBtnActive : ''}`}
                >
                  <span className={styles.btnIcon}>
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <rect x="2" y="5" width="20" height="14" rx="2" ry="2" />
                      <line x1="2" y1="10" x2="22" y2="10" />
                    </svg>
                  </span>
                  <span className={styles.btnLabel}>Spending</span>
                </button>
                <button
                  onClick={() => { setMetricTab('INCOME'); setChartType('pie'); }}
                  className={`${styles.segmentBtn} ${metricTab === 'INCOME' ? styles.segmentBtnActive : ''}`}
                >
                  <span className={styles.btnIcon}>
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <rect x="2" y="6" width="20" height="12" rx="2" />
                      <circle cx="12" cy="12" r="2" />
                      <path d="M6 12h.01M18 12h.01" />
                    </svg>
                  </span>
                  <span className={styles.btnLabel}>Income</span>
                </button>
                <button
                  onClick={() => { setMetricTab('SAVINGS'); setChartType('pie'); }}
                  className={`${styles.segmentBtn} ${metricTab === 'SAVINGS' ? styles.segmentBtnActive : ''}`}
                >
                  <span className={styles.btnIcon}>
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M19 5c-1.5 0-2.8 1.4-3 2-2.2-1.9-5.5-1.9-7.7 0C8 6.4 6.7 5 5 5 3 5 2 7 2 9c0 4 3 7 6 7h1c.6 0 1.1-.4 1.3-.9l.5-1.1c.3-.7 1-.9 1.7-.9h1c.7 0 1.4.2 1.7.9l.5 1.1c.2.5.7.9 1.3.9h1c3 0 6-3 6-7 0-2-1-4-3-4z" />
                      <path d="M15 16v3a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1v-2" />
                      <path d="M10 16v3a1 1 0 0 1-1 1H8a1 1 0 0 1-1-1v-2" />
                      <path d="M9 10h.01" />
                    </svg>
                  </span>
                  <span className={styles.btnLabel}>Savings</span>
                </button>
                <button
                  onClick={() => { setMetricTab('NET_CASH_FLOW'); setChartType('line'); }}
                  className={`${styles.segmentBtn} ${metricTab === 'NET_CASH_FLOW' ? styles.segmentBtnActive : ''}`}
                >
                  <span className={styles.btnIcon}>
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <line x1="12" y1="2" x2="12" y2="22" />
                      <line x1="5" y1="7" x2="19" y2="7" />
                      <path d="M19 7l2 6h-6l2-6M5 7l2 6H1l2-6" />
                    </svg>
                  </span>
                  <span className={styles.btnLabel}>Net Cash Flow</span>
                </button>
                <button
                  onClick={() => { setMetricTab('BALANCE_EVOLUTION'); setChartType('line'); }}
                  className={`${styles.segmentBtn} ${metricTab === 'BALANCE_EVOLUTION' ? styles.segmentBtnActive : ''}`}
                >
                  <span className={styles.btnIcon}>
                    <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                      <path d="M3 21h18M3 10h18M5 6l7-4 7 4M4 10v11M20 10v11M8 14v3M12 14v3M16 14v3" />
                    </svg>
                  </span>
                  <span className={styles.btnLabel}>Balance Evolution</span>
                </button>
              </div>

              {/* Chart Visual Type Toggles */}
              {metricTab !== 'NET_CASH_FLOW' && metricTab !== 'BALANCE_EVOLUTION' && (
                <div className={styles.chartTypeToggle}>
                  <button
                    onClick={() => setChartType('pie')}
                    className={`${styles.chartToggleBtn} ${chartType === 'pie' ? styles.chartToggleBtnActive : ''}`}
                    title="Donut Chart"
                  >
                    <span className={styles.btnIcon}>
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M21.21 15.89A10 10 0 1 1 8 2.83" />
                        <path d="M22 12A10 10 0 0 0 12 2v10z" />
                      </svg>
                    </span>
                    <span className={styles.btnLabel}>Donut</span>
                  </button>
                  <button
                    onClick={() => setChartType('bar')}
                    className={`${styles.chartToggleBtn} ${chartType === 'bar' ? styles.chartToggleBtnActive : ''}`}
                    title="Bar Chart"
                  >
                    <span className={styles.btnIcon}>
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <line x1="18" y1="20" x2="18" y2="10" />
                        <line x1="12" y1="20" x2="12" y2="4" />
                        <line x1="6" y1="20" x2="6" y2="14" />
                      </svg>
                    </span>
                    <span className={styles.btnLabel}>Bar</span>
                  </button>
                  <button
                    onClick={() => setChartType('line')}
                    className={`${styles.chartToggleBtn} ${chartType === 'line' ? styles.chartToggleBtnActive : ''}`}
                    title="Trend Line"
                  >
                    <span className={styles.btnIcon}>
                      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <path d="M3 3v18h18" />
                        <path d="M18.7 8l-5.1 5.2-2.8-2.7L7 14.3" />
                      </svg>
                    </span>
                    <span className={styles.btnLabel}>Line</span>
                  </button>
                </div>
              )}
            </div>

            {/* CHART DISPLAY WINDOW */}
            <div className={styles.chartWrapper}>
              {chartData.length === 0 ? (
                <div className={styles.emptyChart}>
                  <span>📭</span>
                  <p>No matching transactions found for the selected parameters.</p>
                </div>
              ) : (
                <ResponsiveContainer width="100%" height={isMobile ? 240 : 320}>
                  {chartType === 'pie' && metricTab !== 'NET_CASH_FLOW' && metricTab !== 'BALANCE_EVOLUTION' ? (
                    <PieChart>
                      <Pie
                        data={chartData}
                        cx="50%"
                        cy="50%"
                        innerRadius={60}
                        outerRadius={90}
                        paddingAngle={2}
                        dataKey="value"
                        nameKey="name"
                        label={({ name, percent }) => `${name} (${(percent * 100).toFixed(0)}%)`}
                      >
                        {chartData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Pie>
                      <Tooltip formatter={(value) => fmt(value, defaultCurrency)} />
                    </PieChart>
                  ) : chartType === 'bar' && metricTab !== 'NET_CASH_FLOW' && metricTab !== 'BALANCE_EVOLUTION' ? (
                    <BarChart data={chartData} margin={isMobile ? { top: 10, right: 5, left: -25, bottom: 0 } : { top: 10, right: 10, left: 10, bottom: 20 }}>
                      <XAxis dataKey="name" stroke="var(--md-outline)" fontSize={isMobile ? 10 : 12} />
                      <YAxis stroke="var(--md-outline)" fontSize={isMobile ? 10 : 12} tickFormatter={formatCompact} width={isMobile ? 30 : 45} />
                      <Tooltip formatter={(value) => fmt(value, defaultCurrency)} />
                      <Bar dataKey="value" radius={[4, 4, 0, 0]}>
                        {chartData.map((entry, index) => (
                          <Cell key={`cell-${index}`} fill={entry.color} />
                        ))}
                      </Bar>
                    </BarChart>
                  ) : metricTab === 'NET_CASH_FLOW' ? (
                    <AreaChart data={chartData} margin={isMobile ? { top: 10, right: 5, left: -25, bottom: 0 } : { top: 10, right: 10, left: 10, bottom: 20 }}>
                      <defs>
                        <linearGradient id="colorNet" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="var(--md-primary)" stopOpacity={0.4}/>
                          <stop offset="95%" stopColor="var(--md-primary)" stopOpacity={0.0}/>
                        </linearGradient>
                      </defs>
                      <XAxis dataKey="date" stroke="var(--md-outline)" fontSize={isMobile ? 9 : 11} />
                      <YAxis stroke="var(--md-outline)" fontSize={isMobile ? 10 : 12} tickFormatter={formatCompact} width={isMobile ? 30 : 45} />
                      <Tooltip formatter={(value) => fmt(value, defaultCurrency)} />
                      <Legend />
                      <Area name="Net cash flow" type="monotone" dataKey="netFlow" stroke="var(--md-primary)" strokeWidth={2.5} fillOpacity={1} fill="url(#colorNet)" />
                      <Line name="Income" type="monotone" dataKey="INCOME" stroke="var(--md-success)" strokeWidth={1.5} dot={false} />
                      <Line name="Expenses" type="monotone" dataKey="EXPENSE" stroke="var(--md-error)" strokeWidth={1.5} dot={false} />
                    </AreaChart>
                  ) : metricTab === 'BALANCE_EVOLUTION' ? (
                    <AreaChart data={chartData} margin={isMobile ? { top: 10, right: 5, left: -25, bottom: 0 } : { top: 10, right: 10, left: 10, bottom: 20 }}>
                      <defs>
                        <linearGradient id="colorBal" x1="0" y1="0" x2="0" y2="1">
                          <stop offset="5%" stopColor="var(--md-tertiary)" stopOpacity={0.3}/>
                          <stop offset="95%" stopColor="var(--md-tertiary)" stopOpacity={0.0}/>
                        </linearGradient>
                      </defs>
                      <XAxis dataKey="date" stroke="var(--md-outline)" fontSize={isMobile ? 9 : 11} />
                      <YAxis stroke="var(--md-outline)" fontSize={isMobile ? 10 : 12} tickFormatter={formatCompact} width={isMobile ? 30 : 45} />
                      <Tooltip formatter={(value) => fmt(value, defaultCurrency)} />
                      <Area name="Cumulative assets" type="monotone" dataKey="balance" stroke="var(--md-tertiary)" strokeWidth={3} fillOpacity={1} fill="url(#colorBal)" />
                    </AreaChart>
                  ) : (
                    // Timeline Trend Line for specific Categories (EXPENSE, INCOME, SAVINGS)
                    <LineChart data={chartData} margin={isMobile ? { top: 10, right: 5, left: -25, bottom: 0 } : { top: 10, right: 10, left: 10, bottom: 20 }}>
                      <XAxis dataKey="date" stroke="var(--md-outline)" fontSize={isMobile ? 9 : 11} />
                      <YAxis stroke="var(--md-outline)" fontSize={isMobile ? 10 : 12} tickFormatter={formatCompact} width={isMobile ? 30 : 45} />
                      <Tooltip formatter={(value) => fmt(value, defaultCurrency)} />
                      <Line name="Daily value" type="monotone" dataKey={metricTab} stroke="var(--md-primary)" strokeWidth={2.5} activeDot={{ r: 6 }} />
                    </LineChart>
                  )}
                </ResponsiveContainer>
              )}
            </div>
          </section>

          {/* KPI METRIC CARD OVERVIEW ROW */}
          <section className={styles.kpiRow}>
            <div className={styles.kpiCard}>
              <span className={styles.kpiLabel}>Net cash flow</span>
              <span className={styles.kpiAmount}>{fmt(kpis.current.net, defaultCurrency)}</span>
              <div className={styles.kpiCompareRow}>
                {/* Last Week Variance */}
                <div className={styles.kpiVariance}>
                  <span className={styles.compareTitle}>vs Last week</span>
                  <span className={`${styles.varianceText} ${kpis.lastWeek.diff >= 0 ? styles.positive : styles.negative}`}>
                    {kpis.lastWeek.diff >= 0 ? '▲ +' : '▼ '}{fmt(kpis.lastWeek.diff, defaultCurrency)} ({kpis.lastWeek.pct.toFixed(1)}%)
                  </span>
                </div>
                {/* Last Month Variance */}
                <div className={styles.kpiVariance}>
                  <span className={styles.compareTitle}>vs Last month</span>
                  <span className={`${styles.varianceText} ${kpis.lastMonth.diff >= 0 ? styles.positive : styles.negative}`}>
                    {kpis.lastMonth.diff >= 0 ? '▲ +' : '▼ '}{fmt(kpis.lastMonth.diff, defaultCurrency)} ({kpis.lastMonth.pct.toFixed(1)}%)
                  </span>
                </div>
              </div>
            </div>

            <div className={styles.kpiCard}>
              <span className={styles.kpiLabel}>Savings rate</span>
              <span className={styles.kpiAmount}>{kpis.currentSavingsRate.toFixed(1)}%</span>
              <div className={styles.kpiCompareRow}>
                <div className={styles.kpiVariance}>
                  <span className={styles.compareTitle}>vs Last month</span>
                  <span className={`${styles.varianceText} ${kpis.lastMonthSavingsRate.diff >= 0 ? styles.positive : styles.negative}`}>
                    {kpis.lastMonthSavingsRate.diff >= 0 ? '▲ +' : '▼ '}{kpis.lastMonthSavingsRate.diff.toFixed(1)}% variance
                  </span>
                </div>
                <div className={styles.kpiVariance}>
                  <span className={styles.compareTitle}>Allocated savings</span>
                  <span className={styles.savingsDetailsText}>{fmt(kpis.current.savings, defaultCurrency)} total</span>
                </div>
              </div>
            </div>
          </section>

          {/* ACTIVE BUDGETS & GOALS GRID */}
          <section className={styles.goalsGridSection}>
            <div className={styles.sectionHeader}>
              <h3>Active Goals Progress</h3>
              <p>Performance overview of budgets and savings goals active during this range.</p>
            </div>

            <div className={styles.goalsProgressGrid}>
              {/* Budgets Progress Column */}
              <div className={styles.goalsProgressCol}>
                <h4>Active Budgets</h4>
                {activeBudgetsAndGoals.budgets.length === 0 ? (
                  <p className={styles.emptyGoalsText}>No active budgets for this period.</p>
                ) : (
                  <div className={styles.goalsMiniList}>
                    {activeBudgetsAndGoals.budgets.map(b => {
                      const isDanger = b.pct >= 90;
                      return (
                        <div key={b.id} className={styles.miniGoalCard}>
                          <div className={styles.miniGoalMeta}>
                            <span className={styles.miniGoalTitle}>
                              {getIcon(b.icon)} {b.name}
                            </span>
                            <span className={`${styles.miniGoalPct} ${isDanger ? styles.dangerPct : ''}`}>
                              {b.pct}%
                            </span>
                          </div>
                          <div className={styles.progressTrack} role="progressbar" aria-valuenow={b.pct} aria-valuemin={0} aria-valuemax={100}>
                            <div
                              className={`${styles.progressFill} ${isDanger ? styles.progressDanger : styles.progressPrimary}`}
                              style={{ width: `${Math.min(100, b.pct)}%` }}
                            />
                          </div>
                          <div className={styles.miniGoalFooter}>
                            <span>Spent: {fmt(b.spent, defaultCurrency)}</span>
                            <span>Limit: {fmt(b.limit, defaultCurrency)}</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>

              {/* Savings Goals Progress Column */}
              <div className={styles.goalsProgressCol}>
                <h4>Active Savings goals</h4>
                {activeBudgetsAndGoals.goals.length === 0 ? (
                  <p className={styles.emptyGoalsText}>No active savings goals for this period.</p>
                ) : (
                  <div className={styles.goalsMiniList}>
                    {activeBudgetsAndGoals.goals.map(g => {
                      const achieved = g.pct >= 100;
                      return (
                        <div key={g.id} className={styles.miniGoalCard}>
                          <div className={styles.miniGoalMeta}>
                            <span className={styles.miniGoalTitle}>
                              {getIcon(g.icon)} {g.name}
                            </span>
                            <span className={`${styles.miniGoalPct} ${achieved ? styles.successPct : ''}`}>
                              {g.pct}%
                            </span>
                          </div>
                          <div className={styles.progressTrack} role="progressbar" aria-valuenow={g.pct} aria-valuemin={0} aria-valuemax={100}>
                            <div
                              className={`${styles.progressFill} ${styles.progressTertiary}`}
                              style={{ width: `${Math.min(100, g.pct)}%` }}
                            />
                          </div>
                          <div className={styles.miniGoalFooter}>
                            <span>Saved: {fmt(g.current, defaultCurrency)}</span>
                            <span>Target: {fmt(g.target, defaultCurrency)}</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </div>
          </section>
        </main>
      </div>

      {/* AD-HOC EXCLUSIONS MODAL */}
      {excludeModalOpen && (
        <div
          className={styles.modalBackdrop}
          onClick={(e) => { if (e.target === e.currentTarget) setExcludeModalOpen(false); }}
          role="dialog"
          aria-modal="true"
          aria-label="Manage transaction exclusions"
        >
          <div className={styles.modal}>
            <div className={styles.modalHeader}>
              <h2>Exclude Transactions</h2>
              <button
                onClick={() => setExcludeModalOpen(false)}
                className={styles.modalCloseBtn}
                aria-label="Close modal"
              >
                ✕
              </button>
            </div>
            
            <div className={styles.modalSearchRow}>
              <input
                type="text"
                placeholder="Search transactions by category, amount or notes..."
                value={excludeSearchQuery}
                onChange={(e) => setExcludeSearchQuery(e.target.value)}
                className={styles.modalSearchInput}
                aria-label="Search transactions to exclude"
              />
            </div>

            <div className={styles.modalTableContainer}>
              <table className={styles.modalTable}>
                <thead>
                  <tr>
                    <th scope="col">Exclude</th>
                    <th scope="col">Date</th>
                    <th scope="col">Category</th>
                    <th scope="col">Notes</th>
                    <th scope="col">Amount</th>
                    <th scope="col">Exclude Set</th>
                  </tr>
                </thead>
                <tbody>
                  {modalTransactions.length === 0 ? (
                    <tr>
                      <td colSpan="6" className={styles.tableEmptyRow}>
                        No matching transactions found in the loaded range.
                      </td>
                    </tr>
                  ) : (
                    modalTransactions.map(tx => {
                      const isTxExcluded = preferences.excludedTransactionIds.includes(tx.id);
                      const isRecurrentExcluded = tx.recurrenceRuleId && preferences.excludedRecurrenceIds.includes(tx.recurrenceRuleId);
                      
                      return (
                        <tr key={tx.id} className={isTxExcluded || isRecurrentExcluded ? styles.rowExcluded : ''}>
                          <td>
                            <input
                              type="checkbox"
                              checked={isTxExcluded}
                              disabled={!!isRecurrentExcluded}
                              onChange={(e) => {
                                const checked = e.target.checked;
                                setPreferences(prev => ({
                                  ...prev,
                                  excludedTransactionIds: checked
                                    ? [...prev.excludedTransactionIds, tx.id]
                                    : prev.excludedTransactionIds.filter(id => id !== tx.id),
                                }));
                              }}
                              aria-label={`Exclude transaction of ${fmt(tx.convertedAmount, defaultCurrency)}`}
                            />
                          </td>
                          <td>{new Date(tx.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}</td>
                          <td>
                            <span>{getIcon(tx.categoryIcon)} {tx.categoryName}</span>
                          </td>
                          <td className={styles.tableNotesCol}>{tx.notes || '—'}</td>
                          <td className={styles.tableAmountCol}>{fmt(tx.convertedAmount, defaultCurrency)}</td>
                          <td>
                            {tx.recurrenceRuleId ? (
                              <label className={styles.recurrentExcludeLabel}>
                                <input
                                  type="checkbox"
                                  checked={isRecurrentExcluded}
                                  onChange={(e) => {
                                    const checked = e.target.checked;
                                    setPreferences(prev => ({
                                      ...prev,
                                      excludedRecurrenceIds: checked
                                        ? [...prev.excludedRecurrenceIds, tx.recurrenceRuleId]
                                        : prev.excludedRecurrenceIds.filter(id => id !== tx.recurrenceRuleId),
                                    }));
                                  }}
                                />
                                <span>Exclude All 🔁</span>
                              </label>
                            ) : (
                              <span className={styles.oneOffText}>One-off</span>
                            )}
                          </td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            <div className={styles.modalFooter}>
              <button
                onClick={() => setExcludeModalOpen(false)}
                className={styles.modalDoneBtn}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
