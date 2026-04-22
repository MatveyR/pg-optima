export interface QueryHistoryItem {
    id: string;
    query: string;
    connectionId: number;
    connectionName: string;
    executionTimeMs: number;
    rowCount: number;
    timestamp: number; 
}

const STORAGE_KEY = 'pgoptima_query_history';
const MAX_HISTORY_SIZE = 100; 

export function loadHistory(): QueryHistoryItem[] {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (!stored) return [];
    try {
        return JSON.parse(stored);
    } catch {
        return [];
    }
}

function saveHistoryToStorage(history: QueryHistoryItem[]) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(history));
}

export function saveQueryToHistory(item: Omit<QueryHistoryItem, 'id' | 'timestamp'>): QueryHistoryItem {
    const history = loadHistory();
    const newItem: QueryHistoryItem = {
        ...item,
        id: Date.now().toString(),
        timestamp: Date.now(),
    };
    const updatedHistory = [newItem, ...history].slice(0, MAX_HISTORY_SIZE);
    saveHistoryToStorage(updatedHistory);
    return newItem;
}

export function deleteQueryFromHistory(id: string): void {
    const history = loadHistory();
    const updated = history.filter(item => item.id !== id);
    saveHistoryToStorage(updated);
}

export function clearHistory(): void {
    localStorage.removeItem(STORAGE_KEY);
}