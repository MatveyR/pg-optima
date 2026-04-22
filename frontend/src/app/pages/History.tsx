import { useState, useEffect } from 'react';
import { Clock, Play, Trash2, FileCode, Database, Search } from 'lucide-react';
import { useNavigate } from 'react-router';
import { connectionsApi } from '../../api/connections';
import { ConnectionDTO } from '../../types/api.types';

interface QueryHistoryItem {
  id: string;
  query: string;
  connectionId: number;
  connectionName: string;
  executionTime: number;
  timestamp: Date;
  rowCount: number;
}

// Заглушка – загружаем историю из localStorage
const loadHistoryFromStorage = (): QueryHistoryItem[] => {
  const stored = localStorage.getItem('pgoptima_query_history');
  if (stored) return JSON.parse(stored);
  return [];
};

const saveHistoryToStorage = (history: QueryHistoryItem[]) => {
  localStorage.setItem('pgoptima_query_history', JSON.stringify(history));
};

export function History() {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');
  const [history, setHistory] = useState<QueryHistoryItem[]>([]);
  const [connections, setConnections] = useState<ConnectionDTO[]>([]);

  useEffect(() => {
    loadConnections();
    setHistory(loadHistoryFromStorage());
  }, []);

  const loadConnections = async () => {
    try {
      const { data } = await connectionsApi.getAll();
      setConnections(data);
    } catch (error) {
      console.error('Failed to load connections', error);
    }
  };

  const handleRerun = (query: string) => {
    // Переход на редактор с предзаполненным запросом (можно через state или query param)
    navigate('/', { state: { initialQuery: query } });
  };

  const handleDelete = (id: string) => {
    const newHistory = history.filter(item => item.id !== id);
    setHistory(newHistory);
    saveHistoryToStorage(newHistory);
  };

  const formatTimestamp = (date: Date) => {
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const days = Math.floor(hours / 24);
    if (days > 0) return `${days} дн. назад`;
    if (hours > 0) return `${hours} ч. назад`;
    const minutes = Math.floor(diff / (1000 * 60));
    return `${minutes} мин. назад`;
  };

  const getQueryType = (query: string) => {
    const normalized = query.trim().toUpperCase();
    if (normalized.startsWith('SELECT')) return 'SELECT';
    if (normalized.startsWith('INSERT')) return 'INSERT';
    if (normalized.startsWith('UPDATE')) return 'UPDATE';
    if (normalized.startsWith('DELETE')) return 'DELETE';
    if (normalized.startsWith('CREATE')) return 'CREATE';
    return 'ЗАПРОС';
  };

  const getQueryTypeColor = (type: string) => {
    switch (type) {
      case 'SELECT': return 'var(--pg-info)';
      case 'INSERT': return 'var(--pg-success)';
      case 'UPDATE': return 'var(--pg-warning)';
      case 'DELETE': return 'var(--pg-error)';
      case 'CREATE': return 'var(--pg-accent)';
      default: return 'var(--pg-text-muted)';
    }
  };

  const filteredHistory = history.filter(item =>
      item.query.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.connectionName.toLowerCase().includes(searchQuery.toLowerCase())
  );

  return (
      <div className="p-6 max-w-7xl mx-auto">
        <div className="mb-6">
          <h1 className="text-2xl font-bold mb-1" style={{ color: 'var(--pg-text-white)' }}>История запросов</h1>
          <p className="text-sm" style={{ color: 'var(--pg-text-secondary)' }}>Просмотр и повторный запуск предыдущих SQL-запросов</p>
        </div>

        <div className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4" style={{ color: 'var(--pg-text-muted)' }} />
            <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Поиск запросов..."
                className="w-full pl-10 pr-4 py-2.5 rounded-lg border outline-none transition-colors text-sm"
                style={{ backgroundColor: 'var(--pg-bg-surface)', borderColor: 'var(--pg-border)', color: 'var(--pg-text-primary)' }}
                onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
                onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
            />
          </div>
        </div>

        <div className="space-y-3">
          {filteredHistory.map((item) => {
            const queryType = getQueryType(item.query);
            return (
                <div key={item.id} className="rounded-lg border p-4 transition-all" style={{ backgroundColor: 'var(--pg-bg-surface)', borderColor: 'var(--pg-border)' }}
                     onMouseEnter={(e) => { e.currentTarget.style.borderColor = 'var(--pg-border-light)'; e.currentTarget.style.transform = 'translateX(2px)'; }}
                     onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--pg-border)'; e.currentTarget.style.transform = 'translateX(0)'; }}
                >
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-3 flex-1">
                      <div className="px-2 py-1 rounded text-xs font-bold" style={{ backgroundColor: `${getQueryTypeColor(queryType)}15`, color: getQueryTypeColor(queryType) }}>
                        {queryType}
                      </div>
                      <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                        <Database className="w-3.5 h-3.5" />
                        {item.connectionName}
                      </div>
                      <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                        <Clock className="w-3.5 h-3.5" />
                        {formatTimestamp(new Date(item.timestamp))}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button onClick={() => handleRerun(item.query)} className="p-2 rounded-lg transition-colors" style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-text-secondary)' }}
                              onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)'; e.currentTarget.style.color = 'var(--pg-accent)'; }}
                              onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)'; e.currentTarget.style.color = 'var(--pg-text-secondary)'; }}>
                        <Play className="w-4 h-4" />
                      </button>
                      <button onClick={() => handleDelete(item.id)} className="p-2 rounded-lg transition-colors" style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-text-secondary)' }}
                              onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)'; e.currentTarget.style.color = 'var(--pg-error)'; }}
                              onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)'; e.currentTarget.style.color = 'var(--pg-text-secondary)'; }}>
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>
                  <div className="rounded-lg p-3 mb-3 overflow-x-auto" style={{ backgroundColor: 'var(--pg-bg-editor)' }}>
                    <code className="text-xs font-mono" style={{ color: 'var(--pg-text-secondary)' }}>{item.query}</code>
                  </div>
                  <div className="flex items-center gap-4 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                    <div><span className="font-medium" style={{ color: 'var(--pg-text-primary)' }}>{item.executionTime}мс</span> время выполнения</div>
                    <div><span className="font-medium" style={{ color: 'var(--pg-text-primary)' }}>{item.rowCount.toLocaleString()}</span> строк затронуто</div>
                  </div>
                </div>
            );
          })}
          {filteredHistory.length === 0 && (
              <div className="text-center py-12 rounded-lg border" style={{ backgroundColor: 'var(--pg-bg-surface)', borderColor: 'var(--pg-border)' }}>
                <FileCode className="w-12 h-12 mx-auto mb-3" style={{ color: 'var(--pg-text-muted)' }} />
                <p className="text-sm" style={{ color: 'var(--pg-text-muted)' }}>Запросы не найдены</p>
              </div>
          )}
        </div>
      </div>
  );
}