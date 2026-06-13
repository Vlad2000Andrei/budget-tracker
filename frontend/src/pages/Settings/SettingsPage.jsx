import { useState, useEffect, useMemo } from 'react';
import { useAuth } from '../../context/AuthContext';
import axiosInstance from '../../api/axiosInstance';
import styles from './SettingsPage.module.css';
import { PRESET_ICONS, getCategoryIcon } from '../../api/utils';

const PRESET_COLORS = [
  '#FF5733', // Coral
  '#2A9D8F', // Teal
  '#3357FF', // Blue
  '#33FF57', // Green
  '#9B5DE5', // Purple
  '#F15BB5', // Pink
  '#00F5D4', // Aqua
  '#EE9B00', // Amber
  '#E63946', // Red
  '#457B9D', // Steel
  '#1D3557', // Indigo
  '#A8DADC', // Mint
];

export default function SettingsPage() {
  const { user, refreshUser } = useAuth();
  const [activeTab, setActiveTab] = useState('profile');

  // Profile Form State
  const [defaultCurrency, setDefaultCurrency] = useState(user?.defaultCurrency || 'USD');
  const [savingProfile, setSavingProfile] = useState(false);
  const [profileMessage, setProfileMessage] = useState(null);

  // Categories State
  const [categories, setCategories] = useState([]);
  const [loadingCategories, setLoadingCategories] = useState(false);
  const [categoryTypeFilter, setCategoryTypeFilter] = useState('EXPENSE');

  // Category Editor State
  const [editingCategory, setEditingCategory] = useState(null);
  const [categoryName, setCategoryName] = useState('');
  const [parentCategoryId, setParentCategoryId] = useState('');
  const [selectedColor, setSelectedColor] = useState(PRESET_COLORS[0]);
  const [selectedIcon, setSelectedIcon] = useState(Object.keys(PRESET_ICONS)[0]);
  const [savingCategory, setSavingCategory] = useState(false);
  const [categoryMessage, setCategoryMessage] = useState(null);

  // Sync default currency on load or when user object updates
  useEffect(() => {
    if (user?.defaultCurrency) {
      setDefaultCurrency(user.defaultCurrency);
    }
  }, [user]);

  // Load categories on mount / activeTab change
  useEffect(() => {
    if (activeTab === 'categories') {
      fetchCategories();
    }
  }, [activeTab]);

  // Auto-clear success messages after a few seconds
  useEffect(() => {
    if (profileMessage?.type === 'success') {
      const t = setTimeout(() => setProfileMessage(null), 4000);
      return () => clearTimeout(t);
    }
  }, [profileMessage]);

  useEffect(() => {
    if (categoryMessage?.type === 'success') {
      const t = setTimeout(() => setCategoryMessage(null), 4000);
      return () => clearTimeout(t);
    }
  }, [categoryMessage]);

  const fetchCategories = async () => {
    setLoadingCategories(true);
    setCategoryMessage(null);
    try {
      const response = await axiosInstance.get('/v1/categories');
      setCategories(response.data);
    } catch (err) {
      setCategoryMessage({ type: 'error', text: err.message || 'Failed to load categories.' });
    } finally {
      setLoadingCategories(false);
    }
  };

  // Profile save
  const handleSaveProfile = async (e) => {
    e.preventDefault();
    setSavingProfile(true);
    setProfileMessage(null);
    try {
      await axiosInstance.patch('/v1/users/me', { defaultCurrency });
      await refreshUser();
      setProfileMessage({ type: 'success', text: 'Currency preferences saved successfully!' });
    } catch (err) {
      setProfileMessage({ type: 'error', text: err.message || 'Failed to save currency settings.' });
    } finally {
      setSavingProfile(false);
    }
  };

  // Category CRUD
  const handleSaveCategory = async (e) => {
    e.preventDefault();
    if (!categoryName.trim()) {
      setCategoryMessage({ type: 'error', text: 'Category name is required.' });
      return;
    }

    setSavingCategory(true);
    setCategoryMessage(null);
    try {
      const payload = {
        name: categoryName.trim(),
        type: categoryTypeFilter,
        parentId: parentCategoryId ? parseInt(parentCategoryId) : null,
        color: selectedColor,
        icon: selectedIcon,
      };

      if (editingCategory) {
        await axiosInstance.patch(`/v1/categories/${editingCategory.id}`, payload);
        setCategoryMessage({ type: 'success', text: `Category "${payload.name}" updated successfully.` });
      } else {
        await axiosInstance.post('/v1/categories', payload);
        setCategoryMessage({ type: 'success', text: `Category "${payload.name}" created successfully.` });
      }

      resetCategoryForm();
      fetchCategories();
    } catch (err) {
      setCategoryMessage({ type: 'error', text: err.message || 'Failed to save category.' });
    } finally {
      setSavingCategory(false);
    }
  };

  const handleEditCategory = (cat) => {
    setEditingCategory(cat);
    setCategoryName(cat.name);
    setParentCategoryId(cat.parentId ? cat.parentId.toString() : '');
    setSelectedColor(cat.color || PRESET_COLORS[0]);
    setSelectedIcon(cat.icon || Object.keys(PRESET_ICONS)[0]);
    setCategoryMessage(null);
  };

  const handleDeleteCategory = async (id, name) => {
    if (!window.confirm(`Are you sure you want to delete the category "${name}"?`)) return;
    setCategoryMessage(null);
    try {
      await axiosInstance.delete(`/v1/categories/${id}`);
      setCategoryMessage({ type: 'success', text: `Category "${name}" deleted successfully.` });
      // Reset form if the deleted category was being edited
      if (editingCategory?.id === id) {
        resetCategoryForm();
      }
      fetchCategories();
    } catch (err) {
      setCategoryMessage({ type: 'error', text: err.message || 'Failed to delete category.' });
    }
  };

  const resetCategoryForm = () => {
    setEditingCategory(null);
    setCategoryName('');
    setParentCategoryId('');
    setSelectedColor(PRESET_COLORS[0]);
    setSelectedIcon(Object.keys(PRESET_ICONS)[0]);
  };

  // Group active categories into tree
  const categoryTree = useMemo(() => {
    const filtered = categories.filter(c => c.type === categoryTypeFilter);
    const parents = filtered.filter(c => c.parentId === null);
    const children = filtered.filter(c => c.parentId !== null);

    return parents.map(p => ({
      ...p,
      children: children.filter(c => c.parentId === p.id),
    }));
  }, [categories, categoryTypeFilter]);

  // Parent Category options (for nesting child categories)
  const parentOptions = useMemo(() => {
    return categories.filter(
      c =>
        c.type === categoryTypeFilter &&
        c.parentId === null &&
        c.id !== editingCategory?.id
    );
  }, [categories, categoryTypeFilter, editingCategory]);

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.headerIcon}>⚙️</span>
        <div className={styles.headerText}>
          <h1>Settings</h1>
          <p>Configure default application preferences and manage custom categories.</p>
        </div>
      </div>

      {/* Tabs */}
      <div className={styles.tabs} role="tablist">
        <button
          className={`${styles.tabButton} ${activeTab === 'profile' ? styles.activeTabButton : ''}`}
          onClick={() => setActiveTab('profile')}
          role="tab"
          aria-selected={activeTab === 'profile'}
        >
          Profile & Currency
        </button>
        <button
          className={`${styles.tabButton} ${activeTab === 'categories' ? styles.activeTabButton : ''}`}
          onClick={() => setActiveTab('categories')}
          role="tab"
          aria-selected={activeTab === 'categories'}
        >
          Category Manager
        </button>
      </div>

      <div className={styles.contentArea}>
        {/* Tab 1: Profile & Currency */}
        {activeTab === 'profile' && (
          <div className={styles.profileLayout}>
            <div className={styles.profileHero}>
              <div className={styles.avatar}>{user?.email ? user.email.charAt(0).toUpperCase() : 'U'}</div>
              <div className={styles.profileInfo}>
                <h2>Registered User</h2>
                <p>{user?.email || 'Resolving profile...'}</p>
              </div>
            </div>

            <div className={styles.card}>
              <form onSubmit={handleSaveProfile} className={styles.categoryForm}>
                <div className={styles.formGroup}>
                  <label htmlFor="currency-select" className={styles.label}>Default Currency</label>
                  <select
                    id="currency-select"
                    value={defaultCurrency}
                    onChange={(e) => setDefaultCurrency(e.target.value)}
                    className={styles.select}
                  >
                    <option value="USD">USD ($) — United States Dollar</option>
                    <option value="EUR">EUR (€) — Euro</option>
                    <option value="RON">RON (lei) — Romanian Leu</option>
                    <option value="GBP">GBP (£) — British Pound</option>
                    <option value="CAD">CAD ($) — Canadian Dollar</option>
                    <option value="CHF">CHF (Fr.) — Swiss Franc</option>
                    <option value="AUD">AUD ($) — Australian Dollar</option>
                    <option value="JPY">JPY (¥) — Japanese Yen</option>
                  </select>
                  <span className={styles.helpText}>
                    All balances, monthly cash flow charts, and active budgets will automatically convert to and render in this currency.
                  </span>
                </div>

                {profileMessage && (
                  <div
                    className={`${styles.alert} ${profileMessage.type === 'success' ? styles.alertSuccess : styles.alertError}`}
                    role="alert"
                  >
                    {profileMessage.text}
                  </div>
                )}

                <div className={styles.actions}>
                  <button
                    type="submit"
                    disabled={savingProfile}
                    className={`${styles.btn} ${styles.btnPrimary}`}
                  >
                    {savingProfile ? 'Saving...' : 'Save Changes'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Tab 2: Category Manager */}
        {activeTab === 'categories' && (
          <div className={styles.categoryLayout}>
            {/* Category Tree View */}
            <div className={styles.categorySidebar}>
              <div className={styles.typeFilterTabs}>
                <button
                  onClick={() => { setCategoryTypeFilter('EXPENSE'); resetCategoryForm(); }}
                  className={`${styles.typeFilterBtn} ${categoryTypeFilter === 'EXPENSE' ? styles.typeFilterBtnActive : ''}`}
                >
                  Expenses
                </button>
                <button
                  onClick={() => { setCategoryTypeFilter('INCOME'); resetCategoryForm(); }}
                  className={`${styles.typeFilterBtn} ${categoryTypeFilter === 'INCOME' ? styles.typeFilterBtnActive : ''}`}
                >
                  Income
                </button>
                <button
                  onClick={() => { setCategoryTypeFilter('SAVINGS'); resetCategoryForm(); }}
                  className={`${styles.typeFilterBtn} ${categoryTypeFilter === 'SAVINGS' ? styles.typeFilterBtnActive : ''}`}
                >
                  Savings
                </button>
              </div>

              {categoryMessage && (
                <div
                  className={`${styles.alert} ${categoryMessage.type === 'success' ? styles.alertSuccess : styles.alertError}`}
                  role="alert"
                >
                  {categoryMessage.text}
                </div>
              )}

              {loadingCategories ? (
                <div style={{ textAlign: 'center', padding: '24px' }}>Loading categories...</div>
              ) : categoryTree.length === 0 ? (
                <div style={{ textAlign: 'center', padding: '24px', color: 'var(--md-on-surface-variant)' }}>
                  No categories defined for this type.
                </div>
              ) : (
                <div className={styles.categoryTreeList}>
                  {categoryTree.map(parent => (
                    <div key={parent.id} className={styles.categoryGroupCard}>
                      {/* Parent Category Row */}
                      <div className={styles.parentRow}>
                        <div className={styles.categoryMainInfo}>
                          <span
                            className={styles.colorDot}
                            style={{ backgroundColor: parent.color || '#BEC9C7' }}
                          />
                          <span className={styles.iconDisplay}>
                            {getCategoryIcon(parent.icon)}
                          </span>
                          <span className={styles.categoryName}>{parent.name}</span>
                          {parent.systemWide && <span className={styles.categoryBadge}>System</span>}
                        </div>

                        {!parent.systemWide && (
                          <div className={styles.categoryActions}>
                            <button
                              onClick={() => handleEditCategory(parent)}
                              aria-label={`Edit ${parent.name}`}
                              className={styles.actionIconBtn}
                            >
                              ✏️
                            </button>
                            <button
                              onClick={() => handleDeleteCategory(parent.id, parent.name)}
                              aria-label={`Delete ${parent.name}`}
                              className={`${styles.actionIconBtn} ${styles.actionIconBtnDanger}`}
                            >
                              🗑️
                            </button>
                          </div>
                        )}
                      </div>

                      {/* Nested Children List */}
                      {parent.children.length > 0 && (
                        <div className={styles.childrenList}>
                          {parent.children.map(child => (
                            <div key={child.id} className={styles.childRow}>
                              <div className={styles.categoryMainInfo}>
                                <span
                                  className={styles.colorDot}
                                  style={{ backgroundColor: child.color || '#BEC9C7' }}
                                />
                                <span className={styles.iconDisplay}>
                                  {getCategoryIcon(child.icon)}
                                </span>
                                <span className={styles.childName}>{child.name}</span>
                                {child.systemWide && <span className={styles.categoryBadge}>System</span>}
                              </div>

                              {!child.systemWide && (
                                <div className={styles.categoryActions}>
                                  <button
                                    onClick={() => handleEditCategory(child)}
                                    aria-label={`Edit ${child.name}`}
                                    className={styles.actionIconBtn}
                                  >
                                    ✏️
                                  </button>
                                  <button
                                    onClick={() => handleDeleteCategory(child.id, child.name)}
                                    aria-label={`Delete ${child.name}`}
                                    className={`${styles.actionIconBtn} ${styles.actionIconBtnDanger}`}
                                  >
                                    🗑️
                                  </button>
                                </div>
                              )}
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Category Editor Form */}
            <div className={styles.card}>
              <div className={styles.formHeader}>
                <h2>{editingCategory ? 'Edit Category' : 'New Category'}</h2>
                {editingCategory && (
                  <button onClick={resetCategoryForm} className={styles.cancelEditBtn}>
                    Cancel Edit
                  </button>
                )}
              </div>

              <form onSubmit={handleSaveCategory} className={styles.categoryForm}>
                <div className={styles.formGroup}>
                  <label htmlFor="cat-name" className={styles.label}>Category Name</label>
                  <input
                    id="cat-name"
                    type="text"
                    required
                    value={categoryName}
                    onChange={(e) => setCategoryName(e.target.value)}
                    placeholder="e.g. Subscriptions, Rent, Coffee"
                    className={styles.input}
                  />
                </div>

                <div className={styles.formGroup}>
                  <label htmlFor="cat-parent" className={styles.label}>Parent Category (Optional)</label>
                  <select
                    id="cat-parent"
                    value={parentCategoryId}
                    onChange={(e) => setParentCategoryId(e.target.value)}
                    className={styles.select}
                  >
                    <option value="">None (Make it a Parent Category)</option>
                    {parentOptions.map(p => (
                      <option key={p.id} value={p.id}>{p.name}</option>
                    ))}
                  </select>
                  <span className={styles.helpText}>
                    Nesting a category under a parent allows hierarchical budget aggregation (e.g. Spent on Groceries will count towards parent Food budget).
                  </span>
                </div>

                <div className={styles.formGroup}>
                  <span className={styles.label}>Category Color</span>
                  <div className={styles.pickerGrid}>
                    {PRESET_COLORS.map(c => (
                      <button
                        key={c}
                        type="button"
                        style={{ backgroundColor: c }}
                        onClick={() => setSelectedColor(c)}
                        className={`${styles.colorPickerOption} ${selectedColor === c ? styles.colorPickerOptionSelected : ''}`}
                        aria-label={`Select color ${c}`}
                      />
                    ))}
                  </div>
                </div>

                <div className={styles.formGroup}>
                  <span className={styles.label}>Category Icon</span>
                  <div className={styles.pickerGrid}>
                    {Object.entries(PRESET_ICONS).map(([key, emoji]) => (
                      <button
                        key={key}
                        type="button"
                        onClick={() => setSelectedIcon(key)}
                        className={`${styles.iconPickerOption} ${selectedIcon === key ? styles.iconPickerOptionSelected : ''}`}
                        aria-label={`Select icon ${key}`}
                      >
                        {emoji}
                      </button>
                    ))}
                  </div>
                </div>

                <div className={styles.actions}>
                  <button
                    type="submit"
                    disabled={savingCategory}
                    className={`${styles.btn} ${styles.btnPrimary}`}
                  >
                    {savingCategory
                      ? 'Saving...'
                      : editingCategory
                      ? 'Update Category'
                      : 'Create Category'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
