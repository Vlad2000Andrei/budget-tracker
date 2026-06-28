import { createContext, useCallback, useContext, useEffect, useMemo, useState, useRef } from 'react';
import axiosInstance from '../api/axiosInstance';
import { useAuth } from './AuthContext';

const DataContext = createContext(null);

export function DataProvider({ children }) {
  const { isAuthenticated } = useAuth();

  const [categories, setCategories] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [savingsGoals, setSavingsGoals] = useState([]);
  const [dashboardSummary, setDashboardSummary] = useState(null);
  const [recurrenceRules, setRecurrenceRules] = useState([]);

  const categoriesRef = useRef([]);
  const accountsRef = useRef([]);
  const budgetsRef = useRef([]);
  const savingsGoalsRef = useRef([]);
  const dashboardSummaryRef = useRef(null);
  const recurrenceRulesRef = useRef([]);

  useEffect(() => { categoriesRef.current = categories; }, [categories]);
  useEffect(() => { accountsRef.current = accounts; }, [accounts]);
  useEffect(() => { budgetsRef.current = budgets; }, [budgets]);
  useEffect(() => { savingsGoalsRef.current = savingsGoals; }, [savingsGoals]);
  useEffect(() => { dashboardSummaryRef.current = dashboardSummary; }, [dashboardSummary]);
  useEffect(() => { recurrenceRulesRef.current = recurrenceRules; }, [recurrenceRules]);

  const [loading, setLoading] = useState({
    categories: false,
    accounts: false,
    budgets: false,
    savingsGoals: false,
    dashboardSummary: false,
    recurrenceRules: false,
  });

  const fetchCategories = useCallback(async (force = false) => {
    if (!force && categoriesRef.current.length > 0) return;
    setLoading(prev => ({ ...prev, categories: true }));
    try {
      const res = await axiosInstance.get('/v1/categories');
      setCategories(res.data);
    } catch (err) {
      console.error('Failed to fetch categories:', err);
    } finally {
      setLoading(prev => ({ ...prev, categories: false }));
    }
  }, []);

  const fetchAccounts = useCallback(async (force = false) => {
    if (!force && accountsRef.current.length > 0) return;
    setLoading(prev => ({ ...prev, accounts: true }));
    try {
      const res = await axiosInstance.get('/v1/accounts');
      setAccounts(res.data);
    } catch (err) {
      console.error('Failed to fetch accounts:', err);
    } finally {
      setLoading(prev => ({ ...prev, accounts: false }));
    }
  }, []);

  const fetchBudgets = useCallback(async (force = false) => {
    if (!force && budgetsRef.current.length > 0) return;
    setLoading(prev => ({ ...prev, budgets: true }));
    try {
      const res = await axiosInstance.get('/v1/budgets');
      setBudgets(res.data);
    } catch (err) {
      console.error('Failed to fetch budgets:', err);
    } finally {
      setLoading(prev => ({ ...prev, budgets: false }));
    }
  }, []);

  const fetchSavingsGoals = useCallback(async (force = false) => {
    if (!force && savingsGoalsRef.current.length > 0) return;
    setLoading(prev => ({ ...prev, savingsGoals: true }));
    try {
      const res = await axiosInstance.get('/v1/savings-goals');
      setSavingsGoals(res.data);
    } catch (err) {
      console.error('Failed to fetch savings goals:', err);
    } finally {
      setLoading(prev => ({ ...prev, savingsGoals: false }));
    }
  }, []);

  const fetchDashboardSummary = useCallback(async (force = false) => {
    if (!force && dashboardSummaryRef.current !== null) return;
    setLoading(prev => ({ ...prev, dashboardSummary: true }));
    try {
      const res = await axiosInstance.get('/v1/dashboard-summary');
      setDashboardSummary(res.data);
      if (res.data?.accounts) {
        setAccounts(res.data.accounts); // sync accounts with summary
      }
    } catch (err) {
      console.error('Failed to fetch dashboard summary:', err);
    } finally {
      setLoading(prev => ({ ...prev, dashboardSummary: false }));
    }
  }, []);

  const fetchRecurrenceRules = useCallback(async (force = false) => {
    if (!force && recurrenceRulesRef.current.length > 0) return;
    setLoading(prev => ({ ...prev, recurrenceRules: true }));
    try {
      const res = await axiosInstance.get('/v1/recurrence-rules');
      setRecurrenceRules(res.data);
    } catch (err) {
      console.error('Failed to fetch recurrence rules:', err);
    } finally {
      setLoading(prev => ({ ...prev, recurrenceRules: false }));
    }
  }, []);

  const fetchInitialData = useCallback(async (force = false) => {
    await Promise.all([
      fetchCategories(force),
      fetchBudgets(force),
      fetchSavingsGoals(force),
      fetchDashboardSummary(force),
      fetchRecurrenceRules(force)
    ]);
  }, [fetchCategories, fetchBudgets, fetchSavingsGoals, fetchDashboardSummary, fetchRecurrenceRules]);

  useEffect(() => {
    if (!isAuthenticated) {
      setCategories([]);
      setAccounts([]);
      setBudgets([]);
      setSavingsGoals([]);
      setDashboardSummary(null);
      setRecurrenceRules([]);
    } else {
      fetchInitialData();
    }
  }, [isAuthenticated, fetchInitialData]);

  const value = useMemo(() => ({
    categories, fetchCategories,
    accounts, fetchAccounts,
    budgets, fetchBudgets,
    savingsGoals, fetchSavingsGoals,
    dashboardSummary, fetchDashboardSummary,
    recurrenceRules, fetchRecurrenceRules,
    fetchInitialData,
    loading
  }), [
    categories, fetchCategories,
    accounts, fetchAccounts,
    budgets, fetchBudgets,
    savingsGoals, fetchSavingsGoals,
    dashboardSummary, fetchDashboardSummary,
    recurrenceRules, fetchRecurrenceRules,
    fetchInitialData,
    loading
  ]);

  return <DataContext.Provider value={value}>{children}</DataContext.Provider>;
}

export function useData() {
  const ctx = useContext(DataContext);
  if (!ctx) throw new Error('useData must be used inside <DataProvider>');
  return ctx;
}
