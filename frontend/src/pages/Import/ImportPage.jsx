import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import axiosInstance from '../../api/axiosInstance';
import { parseImportFile } from '../../utils/importParser';
import { useData } from '../../context/DataContext';
import styles from './ImportPage.module.css';

const SUPPORTED_CURRENCIES = ['USD', 'EUR', 'RON', 'GBP', 'CHF', 'CAD', 'AUD', 'JPY'];

function parseRobustAmount(val) {
  if (val === null || val === undefined || val === '') return 0;
  
  let str = val.toString().replace(/[^0-9.,+-]/g, '').trim();
  
  const lastDot = str.lastIndexOf('.');
  const lastComma = str.lastIndexOf(',');
  
  if (lastComma > lastDot) {
    if (lastDot !== -1) {
      str = str.replace(/\./g, '').replace(',', '.');
    } else {
      const parts = str.split(',');
      if (parts[1] && parts[1].length === 3) {
        str = str.replace(',', '');
      } else {
        str = str.replace(',', '.');
      }
    }
  } else if (lastDot > lastComma) {
    if (lastComma !== -1) {
      str = str.replace(/,/g, '');
    } else {
      const parts = str.split('.');
      if (parts[1] && parts[1].length === 3) {
        str = str.replace(/\./g, '');
      }
    }
  }
  
  const parsed = parseFloat(str);
  return isNaN(parsed) ? 0 : parsed;
}

function parseRobustDate(val) {
  if (val === null || val === undefined || val === '') {
    return new Date();
  }

  if (val instanceof Date) {
    return isNaN(val.getTime()) ? new Date() : val;
  }

  if (typeof val === 'number') {
    if (val > 0) {
      if (val > 1000000000) {
        return new Date(val < 10000000000 ? val * 1000 : val);
      }
      const excelEpoch = new Date(1899, 11, 30);
      const d = new Date(excelEpoch.getTime() + val * 86400 * 1000);
      return isNaN(d.getTime()) ? new Date() : d;
    }
    return new Date();
  }

  let str = val.toString().trim();
  if (!str) return new Date();

  const parsedMs = Date.parse(str);
  if (!isNaN(parsedMs)) {
    return new Date(parsedMs);
  }

  const parts = str.split(/[\sT]+/);
  const datePart = parts[0];
  let timePart = parts[1] || '';

  let year = null;
  let month = null;
  let day = null;

  const ymdMatch = datePart.match(/^(\d{4})[./-](\d{1,2})[./-](\d{1,2})$/);
  if (ymdMatch) {
    year = parseInt(ymdMatch[1], 10);
    month = parseInt(ymdMatch[2], 10) - 1;
    day = parseInt(ymdMatch[3], 10);
  }

  if (year === null) {
    const dmyMatch = datePart.match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{4})$/);
    if (dmyMatch) {
      const a = parseInt(dmyMatch[1], 10);
      const b = parseInt(dmyMatch[2], 10);
      year = parseInt(dmyMatch[3], 10);
      if (a > 12) {
        day = a;
        month = b - 1;
      } else if (b > 12) {
        day = b;
        month = a - 1;
      } else {
        day = a;
        month = b - 1;
      }
    }
  }

  if (year === null) {
    const dmyShortMatch = datePart.match(/^(\d{1,2})[./-](\d{1,2})[./-](\d{2})$/);
    if (dmyShortMatch) {
      const a = parseInt(dmyShortMatch[1], 10);
      const b = parseInt(dmyShortMatch[2], 10);
      const shortYear = parseInt(dmyShortMatch[3], 10);
      year = shortYear + (shortYear < 50 ? 2000 : 1900);
      if (a > 12) {
        day = a;
        month = b - 1;
      } else if (b > 12) {
        day = b;
        month = a - 1;
      } else {
        day = a;
        month = b - 1;
      }
    }
  }

  if (year !== null && month !== null && day !== null) {
    let hours = 0;
    let minutes = 0;
    let seconds = 0;

    if (timePart) {
      const timeMatch = timePart.match(/^(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?/);
      if (timeMatch) {
        hours = parseInt(timeMatch[1], 10);
        minutes = parseInt(timeMatch[2], 10);
        seconds = timeMatch[3] ? parseInt(timeMatch[3], 10) : 0;
      }
    }

    const d = new Date(year, month, day, hours, minutes, seconds);
    if (!isNaN(d.getTime())) {
      return d;
    }
  }

  return new Date();
}

function formatLocalISO(dateObj) {
  const pad = (n) => n.toString().padStart(2, '0');
  const year = dateObj.getFullYear();
  const month = pad(dateObj.getMonth() + 1);
  const day = pad(dateObj.getDate());
  const hours = pad(dateObj.getHours());
  const minutes = pad(dateObj.getMinutes());
  const seconds = pad(dateObj.getSeconds());
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
}

export default function ImportPage() {
  const navigate = useNavigate();

  // API Data from Cache Context
  const {
    accounts,
    categories,
    recurrenceRules: activeRules,
    fetchInitialData
  } = useData();

  const [loadingConfig, setLoadingConfig] = useState(true);
  const [configError, setConfigError] = useState(null);

  // Stepper state
  const [step, setStep] = useState(1);
  const [error, setError] = useState(null);
  const [isImporting, setIsImporting] = useState(false);

  // Step 1: Upload state
  const [selectedFile, setSelectedFile] = useState(null);
  const [dragActive, setDragActive] = useState(false);
  const [targetAccountId, setTargetAccountId] = useState('');
  const [parsedRawData, setParsedRawData] = useState(null); // { headers: [], rows: [][] }

  // Step 2: Mapping state
  const [columnMappings, setColumnMappings] = useState({
    date: '',
    amount: '',
    notes: '',
    currency: '',
    type: '',
    typeIncomeValue: '',
    typeExpenseValue: '',
  });
  const [currencyMappingType, setCurrencyMappingType] = useState('skip'); // 'skip', 'column', 'fixed'
  const [fixedCurrency, setFixedCurrency] = useState('USD');

  // Step 3: Review state
  const [importRows, setImportRows] = useState([]);
  const [editingRowIndex, setEditingRowIndex] = useState(null);
  const [partialImportModal, setPartialImportModal] = useState(null); // null | { filledRows, skippedCount }
  const [successToast, setSuccessToast] = useState(null); // null | { count: number }
  const [step3Error, setStep3Error] = useState(null); // inline error shown next to Confirm button

  // Load config on mount
  useEffect(() => {
    async function loadConfig() {
      try {
        await fetchInitialData();
      } catch (err) {
        setConfigError(err.message || 'Failed to load initial configuration.');
      } finally {
        setLoadingConfig(false);
      }
    }
    loadConfig();
  }, [fetchInitialData]);

  // Auto-dismiss success toast after 4 seconds
  useEffect(() => {
    if (!successToast) return;
    const timer = setTimeout(() => setSuccessToast(null), 4000);
    return () => clearTimeout(timer);
  }, [successToast]);

  // Set global unsaved changes flag for navigation interception
  useEffect(() => {
    window.hasUnsavedImportChanges = (step === 3 && importRows.length > 0);
    if (step !== 3) setStep3Error(null); // clear step-3 inline error when leaving
    return () => {
      window.hasUnsavedImportChanges = false;
    };
  }, [step, importRows]);

  // Window unload blocker
  useEffect(() => {
    const handleBeforeUnload = (e) => {
      if (step === 3) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [step]);

  // Step 1 Handlers
  const handleDrag = (e) => {
    e.preventDefault();
    e.stopPropagation();
    if (e.type === 'dragenter' || e.type === 'dragover') {
      setDragActive(true);
    } else if (e.type === 'dragleave') {
      setDragActive(false);
    }
  };

  const handleDrop = async (e) => {
    e.preventDefault();
    e.stopPropagation();
    setDragActive(false);

    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      await processSelectedFile(e.dataTransfer.files[0]);
    }
  };

  const handleFileSelect = async (e) => {
    if (e.target.files && e.target.files[0]) {
      await processSelectedFile(e.target.files[0]);
    }
  };

  const processSelectedFile = async (file) => {
    setError(null);
    try {
      const parsed = await parseImportFile(file);
      setSelectedFile(file);
      setParsedRawData(parsed);

      // Guess initial mappings based on header name matches
      const initialMap = { date: '', amount: '', notes: '', currency: '', type: '', typeIncomeValue: '', typeExpenseValue: '' };
      parsed.headers.forEach((h, index) => {
        const lower = h.toLowerCase();
        if (lower.includes('date')) initialMap.date = index.toString();
        else if (lower.includes('amount') || lower.includes('value')) initialMap.amount = index.toString();
        else if (lower.includes('notes') || lower.includes('desc') || lower.includes('detail')) initialMap.notes = index.toString();
        else if (lower.includes('curr')) initialMap.currency = index.toString();
        else if (lower.includes('type') || lower.includes('dir') || lower.includes('status')) initialMap.type = index.toString();
      });
      setColumnMappings(initialMap);
    } catch (err) {
      setError(err.message || 'Failed to parse file.');
      setSelectedFile(null);
      setParsedRawData(null);
    }
  };

  const handleRemoveFile = () => {
    setSelectedFile(null);
    setParsedRawData(null);
    setError(null);
  };

  // Navigations between steps
  const goToStep2 = () => {
    if (!selectedFile || !targetAccountId) {
      setError('Please upload a file and select a target account.');
      return;
    }
    setError(null);
    setStep(2);
  };

  const goToStep3 = async () => {
    if (columnMappings.date === '' || columnMappings.amount === '') {
      setError('Date and Amount column mappings are required.');
      return;
    }
    setError(null);

    // Map raw rows into candidate transactions
    const rawRows = parsedRawData.rows;
    const targetAccount = accounts.find(a => a.id === parseInt(targetAccountId));
    const currencyDefault = targetAccount ? targetAccount.currency : 'USD';

    const candidates = rawRows.map((row, index) => {
      const rawDateStr = row[parseInt(columnMappings.date)];
      const rawAmountStr = row[parseInt(columnMappings.amount)];
      const rawNotes = columnMappings.notes !== '' ? row[parseInt(columnMappings.notes)] : '';
      
      let rowCurrency = currencyDefault;
      if (currencyMappingType === 'column' && columnMappings.currency !== '') {
        const fileVal = row[parseInt(columnMappings.currency)];
        if (fileVal) {
          rowCurrency = fileVal.toString().trim().toUpperCase();
        }
      } else if (currencyMappingType === 'fixed') {
        rowCurrency = fixedCurrency.toUpperCase();
      }

      // Clean amount using robust parser that handles currency symbols, dots and commas
      const amountNum = parseRobustAmount(rawAmountStr);

      // Convert date: handle standard and custom formats robustly
      const dateObj = parseRobustDate(rawDateStr);

      // Format LocalDateTime for Spring boot (YYYY-MM-DDTHH:MM:SS) in local timezone to avoid offset shifts
      const isoDateTime = formatLocalISO(dateObj); 

      // Deduce category type based on amount sign or separate type column
      let deducedType = amountNum < 0 ? 'EXPENSE' : 'INCOME';
      if (columnMappings.type !== '') {
        const rawTypeStr = row[parseInt(columnMappings.type)];
        if (rawTypeStr !== null && rawTypeStr !== undefined) {
          const cleanType = rawTypeStr.toString().trim().toLowerCase();
          const userInc = columnMappings.typeIncomeValue ? columnMappings.typeIncomeValue.trim().toLowerCase() : '';
          const userExp = columnMappings.typeExpenseValue ? columnMappings.typeExpenseValue.trim().toLowerCase() : '';
          
          if (userInc && cleanType === userInc) {
            deducedType = 'INCOME';
          } else if (userExp && cleanType === userExp) {
            deducedType = 'EXPENSE';
          } else {
            // Fallbacks based on common words
            if (cleanType.includes('credit') || cleanType.includes('incoming') || cleanType === 'cr' || cleanType === 'in' || cleanType === 'c' || cleanType === 'deposit' || cleanType === 'income') {
              deducedType = 'INCOME';
            } else if (cleanType.includes('debit') || cleanType.includes('outgoing') || cleanType === 'dr' || cleanType === 'out' || cleanType === 'd' || cleanType === 'withdrawal' || cleanType === 'expense') {
              deducedType = 'EXPENSE';
            }
          }
        }
      }

      return {
        key: index.toString(),
        date: isoDateTime,
        amount: Math.abs(amountNum), // Backend requires positive amounts
        currency: rowCurrency,
        notes: rawNotes ? rawNotes.toString().trim() : '',
        importType: deducedType,
        categoryId: '', // Starts unassigned
        transferToAccountId: '',
        savingsType: 'DEPOSIT',
        savingsToAccountId: '',
        recurrenceRule: null, // Custom rule details
        existingRecurrenceRuleId: '', // Linking rule details
        isPotentialDuplicate: false,
        excluded: false,
        existingTransactionId: null,
      };
    });

    // Invoke backend duplicate checker
    try {
      const checkPayload = {
        accountId: parseInt(targetAccountId),
        transactions: candidates.map(c => ({
          date: c.date,
          amount: c.importType === 'EXPENSE' ? -c.amount : c.amount,
        })),
      };

      const dupResponse = await axiosInstance.post('/v1/imports/detect-duplicates', checkPayload);
      const results = dupResponse.data.results;

      // Merge duplicate flags + autofill metadata back to candidates
      const finalized = candidates.map((c, i) => {
        const result = results[i];
        if (!result) return c;

        const isDup = result.potentialDuplicate;
        return {
          ...c,
          isPotentialDuplicate: isDup,
          excluded: isDup, // Duplicates start excluded by default
          existingTransactionId: result.existingTransactionId ?? null,
          // Autofill from duplicate when flagged — user can clear these via "Mark as New"
          importType: isDup && result.importType ? result.importType : c.importType,
          categoryId: isDup && result.categoryId ? result.categoryId.toString() : c.categoryId,
          transferToAccountId: isDup && result.transferToAccountId ? result.transferToAccountId.toString() : c.transferToAccountId,
          savingsType: isDup && result.savingsType ? result.savingsType : c.savingsType,
          savingsToAccountId: isDup && result.savingsToAccountId ? result.savingsToAccountId.toString() : c.savingsToAccountId,
        };
      });

      setImportRows(finalized);
      setStep(3);
    } catch (err) {
      setError(err.message || 'Failed to perform duplicate check on the server.');
    }
  };

  // Step 3 actions
  const handleMarkNotDuplicate = (index) => {
    setImportRows(prev =>
      prev.map((row, i) => {
        if (i !== index) return row;
        return {
          ...row,
          isPotentialDuplicate: false,
          excluded: false,
          // Clear auto-filled category/account fields so user fills them fresh
          // Keep: date, amount, currency, notes, importType (all came from file)
          categoryId: '',
          transferToAccountId: '',
          savingsToAccountId: '',
          savingsType: 'DEPOSIT',
          recurrenceRule: null,
          existingRecurrenceRuleId: '',
        };
      })
    );
  };

  const handleExcludeRow = (index) => {
    setImportRows(prev => prev.filter((_, i) => i !== index));
  };

  const handleFieldChange = (index, field, value) => {
    setImportRows(prev =>
      prev.map((row, i) => {
        if (i === index) {
          const updated = { ...row, [field]: value };
          // If changing Import Type, clean up related conditional fields
          if (field === 'importType') {
            updated.categoryId = '';
            updated.transferToAccountId = '';
            updated.savingsToAccountId = '';
          }
          return updated;
        }
        return row;
      })
    );
  };

  const isRowComplete = (row) => {
    if (row.excluded) return false; // excluded rows never count as ready
    if (row.importType === 'TRANSFER') return !!row.transferToAccountId;
    if (row.importType === 'SAVINGS') return !!row.savingsToAccountId && !!row.categoryId;
    return !!row.categoryId;
  };

  // Rows that are active (not excluded) but incomplete
  const activeRows = importRows.filter(r => !r.excluded);
  const readyRows = activeRows.filter(isRowComplete);

  const executeImport = async (rowsToImport) => {
    setIsImporting(true);
    setError(null);

    const payload = {
      transactions: rowsToImport.map(row => ({
        amount: row.amount,
        currency: row.currency,
        date: row.date,
        notes: row.notes,
        importType: row.importType,
        categoryId: row.categoryId ? parseInt(row.categoryId) : null,
        accountId: parseInt(targetAccountId),
        transferToAccountId: row.importType === 'TRANSFER' ? parseInt(row.transferToAccountId) : null,
        savingsType: row.importType === 'SAVINGS' ? row.savingsType : null,
        savingsToAccountId: row.importType === 'SAVINGS' ? parseInt(row.savingsToAccountId) : null,
        recurrenceRule: row.recurrenceRule,
        existingRecurrenceRuleId: row.existingRecurrenceRuleId ? parseInt(row.existingRecurrenceRuleId) : null,
      })),
    };

    try {
      await axiosInstance.post('/v1/transactions/bulk', payload);
      setSuccessToast({ count: rowsToImport.length });
      setStep(1);
      setSelectedFile(null);
      setParsedRawData(null);
      setImportRows([]);
      setTargetAccountId('');
      setColumnMappings({ date: '', amount: '', notes: '', currency: '', type: '', typeIncomeValue: '', typeExpenseValue: '' });
      navigate('/');
    } catch (err) {
      setStep3Error(err.message || 'Failed to complete import.');
    } finally {
      setIsImporting(false);
    }
  };

  const handleConfirmImport = async () => {
    setStep3Error(null);
    const filledRows = activeRows.filter(isRowComplete);
    const skippedCount = activeRows.length - filledRows.length;

    if (activeRows.length === 0) {
      // Button should be greyed out already, but guard just in case
      return;
    }

    if (skippedCount === 0) {
      await executeImport(activeRows);
      return;
    }

    if (filledRows.length === 0) {
      setStep3Error('No transactions have a category or account assigned yet.');
      return;
    }

    setPartialImportModal({ filledRows, skippedCount });
  };

  const getUniqueTypeValues = () => {
    if (!parsedRawData || columnMappings.type === '') return [];
    const idx = parseInt(columnMappings.type);
    const values = new Set();
    parsedRawData.rows.forEach(row => {
      const val = row[idx];
      if (val !== null && val !== undefined && val.toString().trim() !== '') {
        values.add(val.toString().trim());
      }
    });
    return Array.from(values);
  };

  // Helper arrays for Step 3 selects
  const filteredCategories = (type) => categories.filter(c => c.type === type);
  const otherAccounts = accounts.filter(a => a.id !== parseInt(targetAccountId));
  const savingsAccounts = otherAccounts.filter(a => a.type === 'SAVINGS');

  if (loadingConfig) {
    return (
      <div className={styles.container}>
        {/* Header */}
        <div className={styles.header}>
          <svg viewBox="0 0 24 24" fill="currentColor" width="40" height="40" className={styles.headerIcon} style={{ color: 'var(--md-primary)', flexShrink: 0 }} aria-hidden="true">
            <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13h-3V9h-4v4H7l5 5 5-5z"/>
          </svg>
          <div className={styles.headerText}>
            <h1>Import Bank Extract</h1>
            <p>Parse extracts from CSV, JSON, or Excel files, map column headers, and import transactions.</p>
          </div>
        </div>

        {/* Stepper Indicator */}
        <div className={styles.stepper}>
          <div className={`${styles.step} ${styles.stepActive}`}>
            <span className={styles.stepNumber}>1</span>
            <span className={styles.stepLabel}>Upload File</span>
          </div>
          <div className={styles.stepLine} />
          <div className={styles.step}>
            <span className={styles.stepNumber}>2</span>
            <span className={styles.stepLabel}>Map Headers</span>
          </div>
          <div className={styles.stepLine} />
          <div className={styles.step}>
            <span className={styles.stepNumber}>3</span>
            <span className={styles.stepLabel}>Review & Verify</span>
          </div>
        </div>

        {/* Dynamic Card skeleton */}
        <div className="skeleton" style={{ height: '250px', width: '100%', borderRadius: '12px', marginTop: '2rem' }} />
      </div>
    );
  }

  if (configError) {
    return (
      <div className={styles.container}>
        <div className={styles.alert + ' ' + styles.alertError}>
          <span>{configError}</span>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className={styles.container}>
      {/* Header */}
      <div className={styles.header}>
        <svg viewBox="0 0 24 24" fill="currentColor" width="40" height="40" className={styles.headerIcon} style={{ color: 'var(--md-primary)', flexShrink: 0 }} aria-hidden="true">
          <path d="M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96zM17 13h-3V9h-4v4H7l5 5 5-5z"/>
        </svg>
        <div className={styles.headerText}>
          <h1>Import Bank Extract</h1>
          <p>Parse extracts from CSV, JSON, or Excel files, map column headers, and import transactions.</p>
        </div>
      </div>

      {/* Stepper Indicator */}
      <div className={styles.stepper}>
        <div className={`${styles.step} ${step === 1 ? styles.stepActive : ''} ${step > 1 ? styles.stepCompleted : ''}`}>
          <span className={styles.stepNumber}>{step > 1 ? '✓' : '1'}</span>
          <span className={styles.stepLabel}>Upload File</span>
        </div>
        <div className={`${styles.stepLine} ${step > 1 ? styles.stepLineCompleted : ''}`} />
        <div className={`${styles.step} ${step === 2 ? styles.stepActive : ''} ${step > 2 ? styles.stepCompleted : ''}`}>
          <span className={styles.stepNumber}>{step > 2 ? '✓' : '2'}</span>
          <span className={styles.stepLabel}>Map Headers</span>
        </div>
        <div className={`${styles.stepLine} ${step > 2 ? styles.stepLineCompleted : ''}`} />
        <div className={`${styles.step} ${step === 3 ? styles.stepActive : ''}`}>
          <span className={styles.stepNumber}>3</span>
          <span className={styles.stepLabel}>Review & Verify</span>
        </div>
      </div>

      {error && step !== 3 && (
        <div className={styles.alert + ' ' + styles.alertError}>
          <span>{error}</span>
        </div>
      )}

      {/* STEP 1: Upload file & Account Selection */}
      {step === 1 && (
        <div className={styles.card}>
          <h2 className={styles.cardTitle}>Select Bank Account & File</h2>

          <div className={styles.formGroup}>
            <label htmlFor="import-account" className={styles.label}>Select Target Account</label>
            <select
              id="import-account"
              value={targetAccountId}
              onChange={(e) => setTargetAccountId(e.target.value)}
              className={styles.select}
            >
              <option value="">-- Choose Account --</option>
              {accounts.map(acc => (
                <option key={acc.id} value={acc.id}>
                  {acc.name} ({acc.currency} — {acc.type})
                </option>
              ))}
            </select>
            <span className={styles.helpText}>This is the account that these imported transactions will be associated with.</span>
          </div>

          <div className={styles.formGroup}>
            <span className={styles.label}>Upload Extract File</span>
            {!selectedFile ? (
              <div
                className={`${styles.dropZone} ${dragActive ? styles.dropZoneActive : ''}`}
                onDragEnter={handleDrag}
                onDragLeave={handleDrag}
                onDragOver={handleDrag}
                onDrop={handleDrop}
                onClick={() => document.getElementById('file-input').click()}
              >
                <input
                  id="file-input"
                  type="file"
                  accept=".csv,.json,.xlsx,.xls"
                  onChange={handleFileSelect}
                  className={styles.fileInput}
                />
                <svg viewBox="0 0 24 24" fill="currentColor" width="48" height="48" style={{ color: 'var(--md-outline)' }} aria-hidden="true">
                  <path d="M14 2H6c-1.1 0-1.99.9-1.99 2L4 20c0 1.1.89 2 1.99 2H18c1.1 0 2-.9 2-2V8l-6-6zm2 16H8v-2h8v2zm0-4H8v-2h8v2zm-3-5V3.5L18.5 9H13z" />
                </svg>
                <span className={styles.uploadText}>Drag and drop bank file here, or click to browse</span>
                <span className={styles.uploadSub}>Supports CSV, JSON, and Excel (.xlsx, .xls)</span>
              </div>
            ) : (
              <div className={styles.selectedFileBar}>
                <div className={styles.flexRow}>
                  <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20" style={{ color: 'var(--md-primary)', flexShrink: 0 }} aria-hidden="true">
                    <path d="M20 6h-8l-2-2H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V8c0-1.1-.9-2-2-2zm-1 11H5V8h14v9z" />
                  </svg>
                  <span className={styles.fileName}>{selectedFile.name}</span>
                  <span className={styles.uploadSub}>({(selectedFile.size / 1024).toFixed(2)} KB)</span>
                </div>
                <button className={styles.removeFileBtn} onClick={handleRemoveFile}>✕</button>
              </div>
            )}
          </div>

          <div className={styles.footer}>
            <div />
            <button
              onClick={goToStep2}
              disabled={!selectedFile || !targetAccountId}
              className={`${styles.btn} ${styles.btnPrimary}`}
            >
              Continue to Mapping
            </button>
          </div>
        </div>
      )}

      {/* STEP 2: Header Mapping */}
      {step === 2 && parsedRawData && (
        <div className={styles.card}>
          <h2 className={styles.cardTitle}>Map Column Headers</h2>
          <p className={styles.sub}>Choose which columns in your bank file correspond to transaction fields.</p>

          <div className={styles.mapperGrid}>
            <div className={styles.mappingRow}>
              <div className={styles.mappingInfo}>
                <span className={styles.mappingLabel}>Date Column *</span>
                <span className={styles.mappingSub}>When the transaction occurred</span>
              </div>
              <select
                value={columnMappings.date}
                onChange={(e) => setColumnMappings(prev => ({ ...prev, date: e.target.value }))}
                className={styles.mappingSelect}
              >
                <option value="">-- Map Date --</option>
                {parsedRawData.headers.map((h, i) => (
                  <option key={i} value={i}>{h || `Column ${i + 1}`}</option>
                ))}
              </select>
            </div>

            <div className={styles.mappingRow}>
              <div className={styles.mappingInfo}>
                <span className={styles.mappingLabel}>Amount Column *</span>
                <span className={styles.mappingSub}>The value or size of transaction</span>
              </div>
              <select
                value={columnMappings.amount}
                onChange={(e) => setColumnMappings(prev => ({ ...prev, amount: e.target.value }))}
                className={styles.mappingSelect}
              >
                <option value="">-- Map Amount --</option>
                {parsedRawData.headers.map((h, i) => (
                  <option key={i} value={i}>{h || `Column ${i + 1}`}</option>
                ))}
              </select>
            </div>

            <div className={styles.mappingRow}>
              <div className={styles.mappingInfo}>
                <span className={styles.mappingLabel}>Notes / Description</span>
                <span className={styles.mappingSub}>Details of the item bought (optional)</span>
              </div>
              <select
                value={columnMappings.notes}
                onChange={(e) => setColumnMappings(prev => ({ ...prev, notes: e.target.value }))}
                className={styles.mappingSelect}
              >
                <option value="">-- Skip Note --</option>
                {parsedRawData.headers.map((h, i) => (
                  <option key={i} value={i}>{h || `Column ${i + 1}`}</option>
                ))}
              </select>
            </div>

            <div className={styles.mappingRow}>
              <div className={styles.mappingInfo}>
                <span className={styles.mappingLabel}>Transaction Type Column</span>
                <span className={styles.mappingSub}>Indicates if row is Income/Expense (optional)</span>
              </div>
              <div className={styles.mappingControl}>
                <select
                  value={columnMappings.type}
                  onChange={(e) => setColumnMappings(prev => ({ ...prev, type: e.target.value, typeIncomeValue: '', typeExpenseValue: '' }))}
                  className={styles.mappingSelect}
                  style={{ width: '100%' }}
                >
                  <option value="">-- Deduce from Sign --</option>
                  {parsedRawData.headers.map((h, i) => (
                    <option key={i} value={i}>{h || `Column ${i + 1}`}</option>
                  ))}
                </select>
                
                {columnMappings.type !== '' && (
                  <div style={{ display: 'flex', gap: '8px', width: '100%' }}>
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--md-on-surface-variant)' }}>
                        Income Value
                      </label>
                      <select
                        value={columnMappings.typeIncomeValue}
                        onChange={(e) => setColumnMappings(prev => ({ ...prev, typeIncomeValue: e.target.value }))}
                        className={styles.mappingSelect}
                        style={{ width: '100%', fontSize: '11px', padding: '6px 8px' }}
                      >
                        <option value="">-- Income --</option>
                        {getUniqueTypeValues().map(val => (
                          <option key={val} value={val}>{val}</option>
                        ))}
                      </select>
                    </div>
                    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <label style={{ fontSize: '11px', fontWeight: '600', color: 'var(--md-on-surface-variant)' }}>
                        Expense Value
                      </label>
                      <select
                        value={columnMappings.typeExpenseValue}
                        onChange={(e) => setColumnMappings(prev => ({ ...prev, typeExpenseValue: e.target.value }))}
                        className={styles.mappingSelect}
                        style={{ width: '100%', fontSize: '11px', padding: '6px 8px' }}
                      >
                        <option value="">-- Expense --</option>
                        {getUniqueTypeValues().map(val => (
                          <option key={val} value={val}>{val}</option>
                        ))}
                      </select>
                    </div>
                  </div>
                )}
              </div>
            </div>

            <div className={styles.mappingRow}>
              <div className={styles.mappingInfo}>
                <span className={styles.mappingLabel}>Currency Configuration</span>
                <span className={styles.mappingSub}>Specify currency source for all rows</span>
              </div>
              <div className={styles.flexRow} style={{ gap: '8px' }}>
                <select
                  value={currencyMappingType}
                  onChange={(e) => setCurrencyMappingType(e.target.value)}
                  className={styles.inlineSelect}
                  style={{ width: '155px' }}
                >
                  <option value="skip">Default Account Currency</option>
                  <option value="column">Map to File Column</option>
                  <option value="fixed">Fixed Currency Code</option>
                </select>

                {currencyMappingType === 'column' && (
                  <select
                    value={columnMappings.currency}
                    onChange={(e) => setColumnMappings(prev => ({ ...prev, currency: e.target.value }))}
                    className={styles.mappingSelect}
                    style={{ width: '150px' }}
                  >
                    <option value="">-- Select Column --</option>
                    {parsedRawData.headers.map((h, i) => (
                      <option key={i} value={i}>{h || `Column ${i + 1}`}</option>
                    ))}
                  </select>
                )}

                {currencyMappingType === 'fixed' && (
                  <select
                    value={fixedCurrency}
                    onChange={(e) => setFixedCurrency(e.target.value)}
                    className={styles.mappingSelect}
                    style={{ width: '100px' }}
                  >
                    {SUPPORTED_CURRENCIES.map(curr => (
                      <option key={curr} value={curr}>{curr}</option>
                    ))}
                  </select>
                )}
              </div>
            </div>
          </div>

          <h3 className={styles.cardTitle} style={{ marginTop: '16px' }}>First 3 Rows Preview</h3>
          <div className={styles.tableContainer}>
            <table className={styles.table}>
              <thead>
                <tr>
                  {parsedRawData.headers.map((h, i) => (
                    <th key={i}>{h || `Col ${i + 1}`}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {parsedRawData.rows.slice(0, 3).map((row, rIdx) => (
                  <tr key={rIdx}>
                    {row.map((cell, cIdx) => (
                      <td key={cIdx}>{cell !== null && cell !== undefined ? cell.toString() : ''}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className={styles.footer}>
            <button className={`${styles.btn} ${styles.btnOutlined}`} onClick={() => setStep(1)}>
              Back
            </button>
            <button
              className={`${styles.btn} ${styles.btnPrimary}`}
              disabled={columnMappings.date === '' || columnMappings.amount === ''}
              onClick={goToStep3}
            >
              Parse & Check Duplicates
            </button>
          </div>
        </div>
      )}

      {/* STEP 3: Review Grid */}
      {step === 3 && (
        <div className={styles.card}>
          <h2 className={styles.cardTitle}>Review and Verify Transactions</h2>
          <p className={styles.sub}>
            Ensure all fields are correct. Specify category for each transaction, and toggle types for Transfers (Moves) and Savings.
          </p>

          <div className={styles.tableContainer}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th style={{ width: '130px' }}>Date</th>
                  <th style={{ width: '180px' }}>Notes / Description</th>
                  <th style={{ width: '100px' }}>Amount</th>
                  <th style={{ width: '100px' }}>Type</th>
                  <th style={{ width: '220px' }}>Category / Account Link</th>
                  <th style={{ width: '180px' }}>Warnings</th>
                  <th style={{ width: '100px' }}>Actions</th>
                </tr>
              </thead>
              <tbody>
                {importRows.length === 0 ? (
                  <tr>
                    <td colSpan="7" style={{ textAlign: 'center', padding: '24px' }}>
                      No valid rows found to import (or all rows were excluded).
                    </td>
                  </tr>
                ) : (
                  importRows.map((row, index) => {
                    const isDup = row.isPotentialDuplicate;

                    return (
                      <tr key={row.key} className={`${row.excluded ? styles.excludedRow : ''} ${isDup && !row.excluded ? styles.duplicateRow : ''}`}>
                        {/* Date */}
                        <td>
                          {new Date(row.date).toLocaleDateString()}
                        </td>

                        {/* Notes */}
                        <td>
                          <input
                            type="text"
                            value={row.notes}
                            onChange={(e) => handleFieldChange(index, 'notes', e.target.value)}
                            className={styles.input}
                            style={{ padding: '6px 8px', fontSize: '13px' }}
                          />
                        </td>

                        {/* Amount */}
                        <td style={{ fontWeight: '700' }}>
                          <span style={{ color: row.importType === 'INCOME' ? 'var(--md-success)' : 'inherit' }}>
                            {row.importType === 'INCOME' ? '+' : '-'}
                            {row.amount.toFixed(2)} {row.currency}
                          </span>
                        </td>

                        {/* Type Selector */}
                        <td>
                          <select
                            value={row.importType}
                            onChange={(e) => handleFieldChange(index, 'importType', e.target.value)}
                            className={styles.inlineSelect}
                            style={{ fontSize: '12px', padding: '4px 6px' }}
                          >
                            <option value="EXPENSE">Expense</option>
                            <option value="INCOME">Income</option>
                            <option value="TRANSFER">Transfer</option>
                            <option value="SAVINGS">Savings</option>
                          </select>
                        </td>

                        {/* Category & Conditional Selectors */}
                        <td>
                          <div className={styles.flexRow} style={{ flexWrap: 'wrap', gap: '8px' }}>
                            {/* Category selector for standard / savings transactions */}
                            {row.importType !== 'TRANSFER' && (
                              <select
                                value={row.categoryId}
                                onChange={(e) => handleFieldChange(index, 'categoryId', e.target.value)}
                                className={`${styles.categorySelect} ${row.categoryId === '' ? styles.categoryUnassigned : ''}`}
                              >
                                <option value="">-- Pick Category --</option>
                                {filteredCategories(row.importType === 'SAVINGS' ? 'SAVINGS' : row.importType).map(cat => (
                                  <option key={cat.id} value={cat.id}>{cat.name}</option>
                                ))}
                              </select>
                            )}

                            {/* Conditional Select for Transfers */}
                            {row.importType === 'TRANSFER' && (
                              <select
                                value={row.transferToAccountId}
                                onChange={(e) => handleFieldChange(index, 'transferToAccountId', e.target.value)}
                                className={`${styles.categorySelect} ${row.transferToAccountId === '' ? styles.categoryUnassigned : ''}`}
                              >
                                <option value="">-- Linked Account --</option>
                                {otherAccounts.map(acc => (
                                  <option key={acc.id} value={acc.id}>{acc.name} ({acc.type})</option>
                                ))}
                              </select>
                            )}

                            {/* Conditional Selects for Savings */}
                            {row.importType === 'SAVINGS' && (
                              <>
                                <select
                                  value={row.savingsType}
                                  onChange={(e) => handleFieldChange(index, 'savingsType', e.target.value)}
                                  className={styles.inlineSelect}
                                  style={{ padding: '6px', fontSize: '12px' }}
                                >
                                  <option value="DEPOSIT">Deposit To</option>
                                  <option value="WITHDRAWAL">Withdraw From</option>
                                </select>
                                <select
                                  value={row.savingsToAccountId}
                                  onChange={(e) => handleFieldChange(index, 'savingsToAccountId', e.target.value)}
                                  className={`${styles.categorySelect} ${row.savingsToAccountId === '' ? styles.categoryUnassigned : ''}`}
                                >
                                  <option value="">-- Savings Account --</option>
                                  {savingsAccounts.map(acc => (
                                    <option key={acc.id} value={acc.id}>{acc.name}</option>
                                  ))}
                                </select>
                              </>
                            )}
                          </div>
                        </td>

                        {/* Warnings (Duplicate Check) */}
                        <td>
                          {row.excluded ? (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                              <span className={styles.badge + ' ' + styles.badgeWarning}>Skipped — Duplicate</span>
                              <button
                                type="button"
                                className={`${styles.btn} ${styles.btnPrimary}`}
                                onClick={() => handleMarkNotDuplicate(index)}
                                style={{ padding: '6px 12px', fontSize: '11px', whiteSpace: 'nowrap' }}
                              >
                                Not a Duplicate
                              </button>
                            </div>
                          ) : isDup ? (
                            <span className={styles.badge + ' ' + styles.badgeSuccess}>Importing as new</span>
                          ) : row.existingTransactionId ? (
                            <span className={styles.badge + ' ' + styles.badgeSuccess}>Override Duplication</span>
                          ) : (
                            <span style={{ color: 'var(--md-outline)', fontSize: '12px' }}>—</span>
                          )}
                        </td>

                        {/* Actions */}
                        <td>
                          <div className={styles.rowActions}>
                            {/* Advanced Edit Dialog Trigger */}
                            <button
                              type="button"
                              className={styles.actionBtn}
                              title="Advanced details (Recurrence / Notes)"
                              onClick={() => setEditingRowIndex(index)}
                            >
                              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" aria-hidden="true">
                                <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" />
                              </svg>
                            </button>
                            {/* Exclude Row */}
                            <button
                              type="button"
                              className={styles.actionBtn + ' ' + styles.actionBtnDanger}
                              title="Exclude from import"
                              onClick={() => handleExcludeRow(index)}
                            >
                              <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" aria-hidden="true">
                                <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
                              </svg>
                            </button>
                          </div>
                        </td>
                      </tr>
                    );
                  })
                )}
              </tbody>
            </table>
          </div>

          <div className={styles.mobileCardList}>
            {importRows.length === 0 ? (
              <div className={styles.emptyCardState}>
                No valid rows found to import (or all rows were excluded).
              </div>
            ) : (
              importRows.map((row, index) => {
                const isDup = row.isPotentialDuplicate;
                
                return (
                  <div key={row.key} className={`${styles.mobileCard} ${row.excluded ? styles.excludedRow : ''} ${isDup && !row.excluded ? styles.duplicateRow : ''}`}>
                    {/* Card Header: Date & Actions */}
                    <div className={styles.mobileCardHeader}>
                      <span className={styles.mobileCardDate}>
                        {new Date(row.date).toLocaleDateString()}
                      </span>
                      <div className={styles.rowActions}>
                        {/* Advanced Edit Dialog Trigger */}
                        <button
                          type="button"
                          className={styles.actionBtn}
                          title="Advanced details (Recurrence / Notes)"
                          onClick={() => setEditingRowIndex(index)}
                        >
                          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" aria-hidden="true">
                            <path d="M19.14 12.94c.04-.3.06-.61.06-.94 0-.32-.02-.64-.07-.94l2.03-1.58c.18-.14.23-.41.12-.61l-1.92-3.32c-.12-.22-.37-.29-.59-.22l-2.39.96c-.5-.38-1.03-.7-1.62-.94l-.36-2.54c-.04-.24-.24-.41-.48-.41h-3.84c-.24 0-.43.17-.47.41l-.36 2.54c-.59.24-1.13.57-1.62.94l-2.39-.96c-.22-.08-.47 0-.59.22L2.74 8.87c-.12.21-.08.47.12.61l2.03 1.58c-.05.3-.09.63-.09.94s.02.64.07.94l-2.03 1.58c-.18.14-.23.41-.12.61l1.92 3.32c.12.22.37.29.59.22l2.39-.96c.5.38 1.03.7 1.62.94l.36 2.54c.05.24.24.41.48.41h3.84c.24 0 .44-.17.47-.41l.36-2.54c.59-.24 1.13-.56 1.62-.94l2.39.96c.22.08.47 0 .59-.22l1.92-3.32c.12-.22.07-.47-.12-.61l-2.01-1.58zM12 15.6c-1.98 0-3.6-1.62-3.6-3.6s1.62-3.6 3.6-3.6 3.6 1.62 3.6 3.6-1.62 3.6-3.6 3.6z" />
                          </svg>
                        </button>
                        {/* Exclude Row */}
                        <button
                          type="button"
                          className={styles.actionBtn + ' ' + styles.actionBtnDanger}
                          title="Exclude from import"
                          onClick={() => handleExcludeRow(index)}
                        >
                          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" aria-hidden="true">
                            <path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z" />
                          </svg>
                        </button>
                      </div>
                    </div>

                    {/* Card Description / Notes Input */}
                    <div className={styles.mobileCardField}>
                      <label className={styles.mobileFieldLabel}>Notes / Description</label>
                      <input
                        type="text"
                        value={row.notes}
                        onChange={(e) => handleFieldChange(index, 'notes', e.target.value)}
                        className={styles.input}
                        style={{ padding: '8px 12px', fontSize: '14px' }}
                      />
                    </div>

                    {/* Card Row: Amount & Type Selector */}
                    <div className={styles.mobileCardRow}>
                      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <label className={styles.mobileFieldLabel}>Amount</label>
                        <span style={{ fontSize: '16px', fontWeight: '700', color: row.importType === 'INCOME' ? 'var(--md-success)' : 'inherit' }}>
                          {row.importType === 'INCOME' ? '+' : '-'}
                          {row.amount.toFixed(2)} {row.currency}
                        </span>
                      </div>
                      <div style={{ width: '120px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
                        <label className={styles.mobileFieldLabel}>Type</label>
                        <select
                          value={row.importType}
                          onChange={(e) => handleFieldChange(index, 'importType', e.target.value)}
                          className={styles.inlineSelect}
                          style={{ fontSize: '13px', padding: '6px 10px', width: '100%' }}
                        >
                          <option value="EXPENSE">Expense</option>
                          <option value="INCOME">Income</option>
                          <option value="TRANSFER">Transfer</option>
                          <option value="SAVINGS">Savings</option>
                        </select>
                      </div>
                    </div>

                    {/* Card Row: Category / Account Selectors */}
                    <div className={styles.mobileCardField}>
                      <label className={styles.mobileFieldLabel}>Category / Account Link</label>
                      <div className={styles.flexRow} style={{ flexWrap: 'wrap', gap: '8px', width: '100%' }}>
                        {row.importType !== 'TRANSFER' && (
                          <select
                            value={row.categoryId}
                            onChange={(e) => handleFieldChange(index, 'categoryId', e.target.value)}
                            className={`${styles.categorySelect} ${row.categoryId === '' ? styles.categoryUnassigned : ''}`}
                            style={{ flex: 1, width: '100%', fontSize: '13px', padding: '6px 10px' }}
                          >
                            <option value="">-- Pick Category --</option>
                            {filteredCategories(row.importType === 'SAVINGS' ? 'SAVINGS' : row.importType).map(cat => (
                              <option key={cat.id} value={cat.id}>{cat.name}</option>
                            ))}
                          </select>
                        )}

                        {row.importType === 'TRANSFER' && (
                          <select
                            value={row.transferToAccountId}
                            onChange={(e) => handleFieldChange(index, 'transferToAccountId', e.target.value)}
                            className={`${styles.categorySelect} ${row.transferToAccountId === '' ? styles.categoryUnassigned : ''}`}
                            style={{ flex: 1, width: '100%', fontSize: '13px', padding: '6px 10px' }}
                          >
                            <option value="">-- Linked Account --</option>
                            {otherAccounts.map(acc => (
                              <option key={acc.id} value={acc.id}>{acc.name} ({acc.type})</option>
                            ))}
                          </select>
                        )}

                        {row.importType === 'SAVINGS' && (
                          <>
                            <select
                              value={row.savingsType}
                              onChange={(e) => handleFieldChange(index, 'savingsType', e.target.value)}
                              className={styles.inlineSelect}
                              style={{ padding: '6px 10px', fontSize: '13px', width: '100%' }}
                            >
                              <option value="DEPOSIT">Deposit To</option>
                              <option value="WITHDRAWAL">Withdraw From</option>
                            </select>
                            <select
                              value={row.savingsToAccountId}
                              onChange={(e) => handleFieldChange(index, 'savingsToAccountId', e.target.value)}
                              className={`${styles.categorySelect} ${row.savingsToAccountId === '' ? styles.categoryUnassigned : ''}`}
                              style={{ flex: 1, width: '100%', fontSize: '13px', padding: '6px 10px' }}
                            >
                              <option value="">-- Savings Account --</option>
                              {savingsAccounts.map(acc => (
                                <option key={acc.id} value={acc.id}>{acc.name}</option>
                              ))}
                            </select>
                          </>
                        )}
                      </div>
                    </div>

                    {/* Card Warnings */}
                    {row.excluded ? (
                      <div className={styles.mobileCardWarningBar}>
                        <span className={styles.badge + ' ' + styles.badgeWarning}>Skipped — Duplicate</span>
                        <button
                          type="button"
                          className={`${styles.btn} ${styles.btnPrimary}`}
                          onClick={() => handleMarkNotDuplicate(index)}
                          style={{ padding: '6px 12px', fontSize: '12px' }}
                        >
                          Not a Duplicate
                        </button>
                      </div>
                    ) : isDup ? (
                      <div className={styles.mobileCardWarningBar}>
                        <span className={styles.badge + ' ' + styles.badgeSuccess}>Importing as new</span>
                      </div>
                    ) : row.existingTransactionId && !isDup ? (
                      <div className={styles.mobileCardWarningBar}>
                        <span className={styles.badge + ' ' + styles.badgeSuccess}>Override Duplication</span>
                      </div>
                    ) : null}
                  </div>
                );
              })
            )}
          </div>

          {/* PARTIAL IMPORT MODAL */}
          {partialImportModal && (
            <div className={styles.dialogBackdrop} onClick={() => setPartialImportModal(null)}>
              <div className={styles.dialogCard} onClick={(e) => e.stopPropagation()} style={{ maxWidth: '460px' }}>
                <div className={styles.dialogHeader}>
                  <h3>Some Transactions Are Incomplete</h3>
                  <button className={styles.closeBtn} onClick={() => setPartialImportModal(null)}>✕</button>
                </div>

                <p style={{ margin: '0 0 16px', color: 'var(--md-on-surface-variant)', fontSize: '14px', lineHeight: '1.6' }}>
                  <strong>{partialImportModal.skippedCount}</strong> transaction{partialImportModal.skippedCount !== 1 ? 's are' : ' is'} still missing a category or account assignment and will be skipped.
                  <br />
                  <strong>{partialImportModal.filledRows.length}</strong> transaction{partialImportModal.filledRows.length !== 1 ? 's are' : ' is'} ready to import.
                </p>

                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  <button
                    className={`${styles.btn} ${styles.btnPrimary}`}
                    disabled={isImporting}
                    onClick={async () => {
                      setPartialImportModal(null);
                      await executeImport(partialImportModal.filledRows);
                    }}
                  >
                    {isImporting ? 'Importing...' : `Import ${partialImportModal.filledRows.length} Filled Transaction(s)`}
                  </button>
                  <button
                    className={`${styles.btn} ${styles.btnOutlined}`}
                    onClick={() => setPartialImportModal(null)}
                  >
                    Continue Filling In
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* ADVANCED DETAILS DIALOG */}
          {editingRowIndex !== null && (
            <div className={styles.dialogBackdrop} onClick={() => setEditingRowIndex(null)}>
              <div className={styles.dialogCard} onClick={(e) => e.stopPropagation()}>
                <div className={styles.dialogHeader}>
                  <h3>Advanced Transaction Settings</h3>
                  <button className={styles.closeBtn} onClick={() => setEditingRowIndex(null)}>✕</button>
                </div>

                <div className={styles.formGroup}>
                  <label className={styles.label}>Recurrence Rule Type</label>
                  <select
                    className={styles.select}
                    value={
                      importRows[editingRowIndex].existingRecurrenceRuleId
                        ? 'EXISTING'
                        : importRows[editingRowIndex].recurrenceRule
                        ? 'NEW'
                        : 'NONE'
                    }
                    onChange={(e) => {
                      const choice = e.target.value;
                      if (choice === 'NONE') {
                        handleFieldChange(editingRowIndex, 'recurrenceRule', null);
                        handleFieldChange(editingRowIndex, 'existingRecurrenceRuleId', '');
                      } else if (choice === 'EXISTING') {
                        handleFieldChange(editingRowIndex, 'recurrenceRule', null);
                        if (activeRules.length > 0) {
                          handleFieldChange(editingRowIndex, 'existingRecurrenceRuleId', activeRules[0].id.toString());
                        } else {
                          alert('No active recurrence rules found to link to.');
                        }
                      } else if (choice === 'NEW') {
                        handleFieldChange(editingRowIndex, 'existingRecurrenceRuleId', '');
                        // Initialize a basic new recurrence rule
                        handleFieldChange(editingRowIndex, 'recurrenceRule', {
                          frequency: 'MONTHLY',
                          interval: 1,
                          startDate: importRows[editingRowIndex].date.split('T')[0],
                          endDate: null,
                        });
                      }
                    }}
                  >
                    <option value="NONE">Non-recurring</option>
                    <option value="EXISTING">Link to existing recurring rule template</option>
                    <option value="NEW">Create new recurring rule schedule</option>
                  </select>
                </div>

                {/* Conditional Inputs for New Recurrence */}
                {importRows[editingRowIndex].recurrenceRule && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', borderLeft: '3px solid var(--md-primary)', paddingLeft: '12px' }}>
                    <div className={styles.formGroup}>
                      <label className={styles.label}>Frequency</label>
                      <select
                        className={styles.select}
                        value={importRows[editingRowIndex].recurrenceRule.frequency}
                        onChange={(e) =>
                          handleFieldChange(editingRowIndex, 'recurrenceRule', {
                            ...importRows[editingRowIndex].recurrenceRule,
                            frequency: e.target.value,
                          })
                        }
                      >
                        <option value="DAILY">Daily</option>
                        <option value="WEEKLY">Weekly</option>
                        <option value="MONTHLY">Monthly</option>
                        <option value="YEARLY">Yearly</option>
                      </select>
                    </div>

                    <div className={styles.formGroup}>
                      <label className={styles.label}>Interval (every N periods)</label>
                      <input
                        type="number"
                        min="1"
                        className={styles.input}
                        value={importRows[editingRowIndex].recurrenceRule.interval}
                        onChange={(e) =>
                          handleFieldChange(editingRowIndex, 'recurrenceRule', {
                            ...importRows[editingRowIndex].recurrenceRule,
                            interval: parseInt(e.target.value) || 1,
                          })
                        }
                      />
                    </div>
                  </div>
                )}

                {/* Conditional Inputs for Existing Recurrence */}
                {importRows[editingRowIndex].existingRecurrenceRuleId && (
                  <div className={styles.formGroup}>
                    <label className={styles.label}>Select Existing Schedule</label>
                    <select
                      className={styles.select}
                      value={importRows[editingRowIndex].existingRecurrenceRuleId}
                      onChange={(e) => handleFieldChange(editingRowIndex, 'existingRecurrenceRuleId', e.target.value)}
                    >
                      {activeRules.map(rule => (
                        <option key={rule.id} value={rule.id}>
                          {rule.categoryName || 'Rule'} — {rule.amount} {rule.currency} ({rule.frequency})
                        </option>
                      ))}
                    </select>
                  </div>
                )}

                <div className={styles.footer} style={{ marginTop: '12px' }}>
                  <div />
                  <button className={`${styles.btn} ${styles.btnPrimary}`} onClick={() => setEditingRowIndex(null)}>
                    Save & Close
                  </button>
                </div>
              </div>
            </div>
          )}

          <div className={styles.footer}>
            <button className={`${styles.btn} ${styles.btnOutlined}`} onClick={() => setStep(2)}>
              Back
            </button>
            <div className={styles.footerRight}>
              {step3Error && (
                <span className={styles.footerError}>{step3Error}</span>
              )}
              <button
                className={`${styles.btn} ${styles.btnPrimary}`}
                disabled={activeRows.length === 0 || isImporting}
                onClick={handleConfirmImport}
              >
                {isImporting
                  ? 'Importing...'
                  : readyRows.length === 0
                  ? 'Confirm Import'
                  : readyRows.length < activeRows.length
                  ? `Confirm Import (${readyRows.length} ready, ${activeRows.length - readyRows.length} incomplete)`
                  : `Confirm Import (${readyRows.length} ready)`
                }
              </button>
            </div>
          </div>
        </div>
      )}
    </div>

      {/* SUCCESS TOAST */}
      {successToast && (
        <div className={styles.successToast} role="status" aria-live="polite">
          <svg className={styles.successToastIcon} viewBox="0 0 24 24" fill="currentColor" width="22" height="22" aria-hidden="true">
            <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm-2 14.5l-4.5-4.5 1.41-1.41L10 13.67l7.09-7.09 1.41 1.41L10 16.5z"/>
          </svg>
          <div className={styles.successToastBody}>
            <span className={styles.successToastTitle}>Import Complete</span>
            <span className={styles.successToastMsg}>
              {successToast.count} transaction{successToast.count !== 1 ? 's' : ''} imported successfully.
            </span>
          </div>
          <button
            className={styles.successToastClose}
            onClick={() => setSuccessToast(null)}
            aria-label="Dismiss"
          >
            <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18" aria-hidden="true">
              <path d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/>
            </svg>
          </button>
        </div>
      )}
    </>
  );
}
