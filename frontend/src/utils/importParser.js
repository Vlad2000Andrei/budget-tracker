import Papa from 'papaparse';
import * as XLSX from 'xlsx';

/**
 * Parses an uploaded file (CSV, JSON, Excel) into a standard row-based structure.
 * Returns an object: { headers: string[], rows: any[][] }
 * @param {File} file 
 * @returns {Promise<{ headers: string[], rows: any[][] }>}
 */
export const parseImportFile = (file) => {
  return new Promise((resolve, reject) => {
    const extension = file.name.split('.').pop().toLowerCase();

    if (extension === 'json') {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const data = JSON.parse(e.target.result);
          if (Array.isArray(data) && data.length > 0) {
            const headers = Object.keys(data[0]);
            const rows = data.map(item => headers.map(h => item[h]));
            resolve({ headers, rows });
          } else {
            reject(new Error("JSON file must be an array of objects."));
          }
        } catch (error) {
          reject(new Error("Failed to parse JSON: " + error.message));
        }
      };
      reader.onerror = () => reject(new Error("File reading error."));
      reader.readAsText(file);

    } else if (extension === 'csv') {
      Papa.parse(file, {
        complete: (results) => {
          if (results.errors && results.errors.length > 0 && results.data.length === 0) {
            reject(new Error(results.errors[0].message));
          } else {
            const data = results.data.filter(row => row.some(cell => cell !== null && cell !== ''));
            if (data.length > 0) {
              const headers = data[0];
              const rows = data.slice(1);
              resolve({ headers, rows });
            } else {
              reject(new Error("CSV file is empty."));
            }
          }
        },
        error: (err) => reject(err),
        skipEmptyLines: true,
      });

    } else if (extension === 'xlsx' || extension === 'xls') {
      const reader = new FileReader();
      reader.onload = (e) => {
        try {
          const data = new Uint8Array(e.target.result);
          const workbook = XLSX.read(data, { type: 'array' });
          const firstSheetName = workbook.SheetNames[0];
          const worksheet = workbook.Sheets[firstSheetName];
          const jsonData = XLSX.utils.sheet_to_json(worksheet, { header: 1 });
          
          if (jsonData.length > 0) {
            const headers = jsonData[0].map(h => h ? h.toString() : '');
            const rows = jsonData.slice(1).map(row => 
              Array.from({ length: headers.length }, (_, i) => row[i] !== undefined ? row[i] : null)
            );
            resolve({ headers, rows });
          } else {
            reject(new Error("Excel sheet is empty."));
          }
        } catch (error) {
          reject(new Error("Failed to parse Excel: " + error.message));
        }
      };
      reader.onerror = () => reject(new Error("File reading error."));
      reader.readAsArrayBuffer(file);

    } else {
      reject(new Error(`Unsupported file type: .${extension}`));
    }
  });
};
