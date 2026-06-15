import { useState, useEffect, useMemo } from 'react';
import axiosInstance from '../../api/axiosInstance';
import styles from './CategoriesPage.module.css';
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

export default function CategoriesPage() {
  const [categories, setCategories] = useState([]);
  const [loadingCategories, setLoadingCategories] = useState(false);
  const [categoryTypeFilter, setCategoryTypeFilter] = useState('EXPENSE');

  // Category Editor State
  const [editingCategory, setEditingCategory] = useState(null);
  const [categoryName, setCategoryName] = useState('');
  const [parentCategoryId, setParentCategoryId] = useState('');
  const [selectedColor, setSelectedColor] = useState(PRESET_COLORS[0]);
  const [selectedIcon, setSelectedIcon] = useState(Object.keys(PRESET_ICONS)[0]);
  const [customEmoji, setCustomEmoji] = useState('');
  const [savingCategory, setSavingCategory] = useState(false);
  const [categoryMessage, setCategoryMessage] = useState(null);
  const [isFormOpen, setIsFormOpen] = useState(false);

  // Load categories on mount
  useEffect(() => {
    fetchCategories();
  }, []);

  // Auto-clear success messages after a few seconds
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
      // Notify other components (like add modal) to reload
      window.dispatchEvent(new Event('transaction-added'));
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
    if (cat.icon && cat.icon in PRESET_ICONS) {
      setSelectedIcon(cat.icon);
      setCustomEmoji('');
    } else {
      setSelectedIcon(cat.icon || Object.keys(PRESET_ICONS)[0]);
      setCustomEmoji(cat.icon || '');
    }
    setCategoryMessage(null);
    setIsFormOpen(true);
  };

  const handleDeleteCategory = async (id, name) => {
    if (!window.confirm(`Are you sure you want to delete the category "${name}"?`)) return;
    setCategoryMessage(null);
    try {
      await axiosInstance.delete(`/v1/categories/${id}`);
      setCategoryMessage({ type: 'success', text: `Category "${name}" deleted successfully.` });
      if (editingCategory?.id === id) {
        resetCategoryForm();
      }
      fetchCategories();
      window.dispatchEvent(new Event('transaction-added'));
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
    setCustomEmoji('');
    setIsFormOpen(false);
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
        <span className={styles.headerIcon} role="img" aria-label="Label tag">🏷️</span>
        <div className={styles.headerText}>
          <h1>Categories</h1>
          <p>Organize custom transaction and budget categories with custom icons and colors.</p>
        </div>
        <button
          className={`${styles.btn} ${styles.btnPrimary} ${styles.mobileAddBtn}`}
          onClick={() => setIsFormOpen(true)}
          aria-label="Add Category"
          title="Add Category"
        >
          <span className={styles.mobileAddBtnIcon} aria-hidden="true">+</span>
          <span className={styles.mobileAddBtnLabel}>Add Category</span>
        </button>
      </div>

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
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true" style={{ marginRight: '6px' }}>
                {categoryMessage.type === 'success' ? (
                  <path d="M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z"/>
                ) : (
                  <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-2h2v2zm0-4h-2V7h2v6z"/>
                )}
              </svg>
              <span>{categoryMessage.text}</span>
            </div>
          )}

          {loadingCategories ? (
            <div className={styles.loadingState}>
              <div className="spinner" aria-hidden="true" />
              <span>Loading categories...</span>
            </div>
          ) : categoryTree.length === 0 ? (
            <div className={styles.emptyState}>
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
        <div className={`${styles.sidebarContainer} ${isFormOpen ? styles.isOpen : ''}`}>
          <div className={styles.backdrop} onClick={resetCategoryForm} />
          <div className={`${styles.card} ${styles.sidebarCard}`}>
            {/* Drag handle */}
            <div className={styles.mobileHandle} aria-hidden="true" />
            <div className={styles.formHeader}>
              <h2>{editingCategory ? 'Edit Category' : 'New Category'}</h2>
              <button
                type="button"
                className={styles.closeBtn}
                onClick={resetCategoryForm}
                aria-label="Close form"
              >
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                  <path d="M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z" />
                </svg>
              </button>
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
                {/* Custom Color Selector Option */}
                <div className={styles.customColorContainer}>
                  <input
                    type="color"
                    id="custom-color-picker"
                    value={PRESET_COLORS.includes(selectedColor) ? '#ffffff' : selectedColor.toLowerCase()}
                    onChange={(e) => setSelectedColor(e.target.value.toUpperCase())}
                    className={styles.customColorInput}
                    aria-label="Choose custom color"
                  />
                  <label htmlFor="custom-color-picker" className={`${styles.colorPickerOption} ${styles.customColorOption} ${!PRESET_COLORS.includes(selectedColor) ? styles.colorPickerOptionSelected : ''}`} style={{ backgroundColor: !PRESET_COLORS.includes(selectedColor) ? selectedColor : 'transparent' }}>
                    {!PRESET_COLORS.includes(selectedColor) ? '' : '🎨'}
                  </label>
                </div>
              </div>
            </div>

            <div className={styles.formGroup}>
              <span className={styles.label}>Category Icon</span>
              <div className={styles.pickerGrid}>
                {Object.entries(PRESET_ICONS).map(([key, emoji]) => (
                  <button
                    key={key}
                    type="button"
                    onClick={() => {
                      setSelectedIcon(key);
                      setCustomEmoji('');
                    }}
                    className={`${styles.iconPickerOption} ${selectedIcon === key && !customEmoji ? styles.iconPickerOptionSelected : ''}`}
                    aria-label={`Select icon ${key}`}
                  >
                    {emoji}
                  </button>
                ))}
              </div>
              <div className={styles.customEmojiSection}>
                <label htmlFor="custom-emoji" className={styles.customEmojiLabel}>Or enter custom emoji:</label>
                <div className={styles.customEmojiInputRow}>
                  <input
                    id="custom-emoji"
                    type="text"
                    maxLength="4"
                    placeholder="Enter emoji (e.g. 🍉)"
                    className={styles.input}
                    value={customEmoji}
                    onChange={(e) => {
                      const val = e.target.value;
                      setCustomEmoji(val);
                      if (val.trim()) {
                        setSelectedIcon(val.trim());
                      } else {
                        setSelectedIcon(Object.keys(PRESET_ICONS)[0]);
                      }
                    }}
                  />
                  {customEmoji && (
                    <span className={styles.customEmojiPreview}>
                      {selectedIcon}
                    </span>
                  )}
                </div>
              </div>
            </div>

            <div className={styles.formActions}>
              <button
                type="button"
                className={`${styles.btn} ${styles.btnOutlinedDanger}`}
                onClick={resetCategoryForm}
              >
                Cancel
              </button>
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
      </div>
    </div>
  );
}
