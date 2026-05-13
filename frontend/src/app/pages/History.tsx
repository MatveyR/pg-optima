import { useState, useEffect } from 'react';
import { Clock, Play, Trash2, FileCode, Database, Search, Activity, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router';
import { loadHistory, deleteQueryFromHistory, QueryHistoryItem } from '../../store/historyStorage';
import { analyticsApi } from '../../api/analytics';
import { connectionsApi } from '../../api/connections';
import { ConnectionDTO, SlowQueryDTO } from '../../types/api.types';

type TabType = 'local' | 'pgstat';

export function History() {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<TabType>('local');
  const [searchQuery, setSearchQuery] = useState('');
  const [history, setHistory] = useState<QueryHistoryItem[]>([]);
  const [slowQueries, setSlowQueries] = useState<SlowQueryDTO[]>([]);
  const [connections, setConnections] = useState<ConnectionDTO[]>([]);
  const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);
  const [loadingSlow, setLoadingSlow] = useState(false);
  const [slowError, setSlowError] = useState<string | null>(null);

  useEffect(() => {
    loadHistoryFromStorage();
    loadConnections();
  }, []);

  useEffect(() => {
    if (activeTab === 'pgstat' && selectedConnectionId) {
      fetchSlowQueries();
    }
  }, [activeTab, selectedConnectionId]);

  const loadConnections = async () => {
    try {
      const { data } = await connectionsApi.getAll();
      setConnections(data);
      if (data.length > 0 && !selectedConnectionId) {
        setSelectedConnectionId(data[0].id);
      }
    } catch (error) {
      console.error('Failed to load connections', error);
    }
  };

  const fetchSlowQueries = async () => {
    if (!selectedConnectionId) return;
    setLoadingSlow(true);
    setSlowError(null);
    try {
      const { data } = await analyticsApi.getSlowQueries(selectedConnectionId);
      setSlowQueries(data);
    } catch (error: any) {
      console.error('Failed to fetch slow queries', error);
      setSlowError(error.response?.data?.message || 'Ошибка загрузки медленных запросов');
      setSlowQueries([]);
    } finally {
      setLoadingSlow(false);
    }
  };

  const loadHistoryFromStorage = () => {
    setHistory(loadHistory());
  };

  const handleRerun = (item: QueryHistoryItem) => {
    navigate('/', { state: { initialQuery: item.query, initialConnectionId: item.connectionId } });
  };

  const handleDelete = (id: string) => {
    deleteQueryFromHistory(id);
    setHistory(prev => prev.filter(item => item.id !== id));
  };

  const formatTimestamp = (timestamp: number) => {
    const now = Date.now();
    const diff = now - timestamp;
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

  const filteredSlowQueries = slowQueries.filter(item =>
      item.query.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const renderQueryCard = (
      query: string,
      executionTimeMs: number,
      rowCount: number,
      connectionName?: string,
      timestamp?: number,
      onRerun?: () => void,
      onDelete?: () => void,
      rerunEnabled: boolean = true
  ) => {
    const queryType = getQueryType(query);
    return (
        <div className="rounded-lg border p-4 transition-all" style={{ backgroundColor: 'var(--pg-bg-surface)', borderColor: 'var(--pg-border)' }}
             onMouseEnter={(e) => { e.currentTarget.style.borderColor = 'var(--pg-border-light)'; e.currentTarget.style.transform = 'translateX(2px)'; }}
             onMouseLeave={(e) => { e.currentTarget.style.borderColor = 'var(--pg-border)'; e.currentTarget.style.transform = 'translateX(0)'; }}
        >
          <div className="flex items-start justify-between mb-3">
            <div className="flex items-center gap-3 flex-1 flex-wrap">
              <div className="px-2 py-1 rounded text-xs font-bold" style={{ backgroundColor: `${getQueryTypeColor(queryType)}15`, color: getQueryTypeColor(queryType) }}>
                {queryType}
              </div>
              {connectionName && (
                  <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                    <Database className="w-3.5 h-3.5" />
                    {connectionName}
                  </div>
              )}
              {timestamp && (
                  <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                    <Clock className="w-3.5 h-3.5" />
                    {formatTimestamp(timestamp)}
                  </div>
              )}
            </div>
            <div className="flex items-center gap-2">
              {rerunEnabled && onRerun && (
                  <button onClick={onRerun} className="p-2 rounded-lg transition-colors" style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-text-secondary)' }}
                          onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)'; e.currentTarget.style.color = 'var(--pg-accent)'; }}
                          onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)'; e.currentTarget.style.color = 'var(--pg-text-secondary)'; }}>
                    <Play className="w-4 h-4" />
                  </button>
              )}
              {onDelete && (
                  <button onClick={onDelete} className="p-2 rounded-lg transition-colors" style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-text-secondary)' }}
                          onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)'; e.currentTarget.style.color = 'var(--pg-error)'; }}
                          onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)'; e.currentTarget.style.color = 'var(--pg-text-secondary)'; }}>
                    <Trash2 className="w-4 h-4" />
                  </button>
              )}
            </div>
          </div>
          <div className="rounded-lg p-3 mb-3 overflow-x-auto" style={{ backgroundColor: 'var(--pg-bg-editor)' }}>
            <code className="text-xs font-mono" style={{ color: 'var(--pg-text-secondary)' }}>{query}</code>
          </div>
          <div className="flex items-center gap-4 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
            <div><span className="font-medium" style={{ color: 'var(--pg-text-primary)' }}>{Math.round(executionTimeMs)} мс</span> время выполнения</div>
            <div><span className="font-medium" style={{ color: 'var(--pg-text-primary)' }}>{rowCount.toLocaleString()}</span> строк затронуто</div>
          </div>
        </div>
    );
  };

  return (
      <div className="p-6 max-w-7xl mx-auto">
        <div className="mb-6">
          <h1 className="text-2xl font-bold mb-1" style={{ color: 'var(--pg-text-white)' }}>История запросов</h1>
          <p className="text-sm" style={{ color: 'var(--pg-text-secondary)' }}>Просмотр и повторный запуск предыдущих SQL-запросов</p>
        </div>

        {/* Tabs */}
        <div className="flex gap-2 border-b mb-6" style={{ borderColor: 'var(--pg-border)' }}>
          <button
              onClick={() => setActiveTab('local')}
              className="px-4 py-2 text-sm font-medium transition-colors relative"
              style={{ color: activeTab === 'local' ? 'var(--pg-accent)' : 'var(--pg-text-secondary)' }}
          >
            История (локальная)
            {activeTab === 'local' && <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ backgroundColor: 'var(--pg-accent)' }} />}
          </button>
          <button
              onClick={() => setActiveTab('pgstat')}
              className="px-4 py-2 text-sm font-medium transition-colors relative"
              style={{ color: activeTab === 'pgstat' ? 'var(--pg-accent)' : 'var(--pg-text-secondary)' }}
          >
            Медленные запросы (pg_stat_activity)
            {activeTab === 'pgstat' && <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ backgroundColor: 'var(--pg-accent)' }} />}
          </button>
        </div>

        {/* Search and filters */}
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

        {/* Connection selector for pg_stat_activity tab */}
        {activeTab === 'pgstat' && (
            <div className="mb-4 flex items-center gap-3 flex-wrap">
              <div className="flex items-center gap-2">
                <Database className="w-4 h-4" style={{ color: 'var(--pg-text-muted)' }} />
                <span className="text-sm" style={{ color: 'var(--pg-text-primary)' }}>Подключение:</span>
                <select
                    value={selectedConnectionId ?? ''}
                    onChange={(e) => setSelectedConnectionId(Number(e.target.value))}
                    className="px-3 py-1.5 rounded-lg border text-sm"
                    style={{ backgroundColor: 'var(--pg-bg-card)', borderColor: 'var(--pg-border)', color: 'var(--pg-text-primary)' }}
                >
                  {connections.map(conn => (
                      <option key={conn.id} value={conn.id}>{conn.name}</option>
                  ))}
                </select>
              </div>
              <button
                  onClick={fetchSlowQueries}
                  className="flex items-center gap-2 px-3 py-1.5 rounded-lg text-sm transition-colors"
                  style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-text-secondary)' }}
                  onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)'; e.currentTarget.style.color = 'var(--pg-accent)'; }}
                  onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)'; e.currentTarget.style.color = 'var(--pg-text-secondary)'; }}
              >
                <RefreshCw className="w-3.5 h-3.5" />
                Обновить
              </button>
            </div>
        )}

        {/* Content */}
        <div className="space-y-3">
          {activeTab === 'local' && (
              <>
                {filteredHistory.map((item) => (
                    <div key={item.id}>
                      {renderQueryCard(
                          item.query,
                          item.executionTimeMs,
                          item.rowCount,
                          item.connectionName,
                          item.timestamp,
                          () => handleRerun(item),
                          () => handleDelete(item.id),
                          true
                      )}
                    </div>
                ))}
                {filteredHistory.length === 0 && (
                    <div className="text-center py-12 rounded-lg border" style={{ backgroundColor: 'var(--pg-bg-surface)', borderColor: 'var(--pg-border)' }}>
                      <FileCode className="w-12 h-12 mx-auto mb-3" style={{ color: 'var(--pg-text-muted)' }} />
                      <p className="text-sm" style={{ color: 'var(--pg-text-muted)' }}>Запросы не найдены</p>
                    </div>
                )}
              </>
          )}

          {activeTab === 'pgstat' && (
              <>
                {loadingSlow && (
                    <div className="text-center py-12" style={{ color: 'var(--pg-text-secondary)' }}>
                      <Activity className="w-8 h-8 mx-auto mb-2 animate-pulse" />
                      Загрузка медленных запросов...
                    </div>
                )}
                {slowError && (
                    <div className="p-4 rounded-lg text-center" style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-error)' }}>
                      {slowError}
                    </div>
                )}
                {!loadingSlow && !slowError && filteredSlowQueries.map((item, idx) => (
                    <div key={idx}>
                      {renderQueryCard(
                          item.query,
                          item.meanExecutionTimeMs,
                          item.rows,
                          connections.find(c => c.id === selectedConnectionId)?.name,
                          undefined,
                          () => {
                            // Rerun from slow queries: navigate to editor with this query and selected connection
                            navigate('/', { state: { initialQuery: item.query, initialConnectionId: selectedConnectionId! } });
                          },
                          undefined,
                          true
                      )}
                    </div>
                ))}
                {!loadingSlow && !slowError && filteredSlowQueries.length === 0 && (
                    <div className="text-center py-12 rounded-lg border" style={{ backgroundColor: 'var(--pg-bg-surface)', borderColor: 'var(--pg-border)' }}>
                      <FileCode className="w-12 h-12 mx-auto mb-3" style={{ color: 'var(--pg-text-muted)' }} />
                      <p className="text-sm" style={{ color: 'var(--pg-text-muted)' }}>Медленные запросы не найдены (mean_exec_time больше 250 мс)</p>
                      <p className="text-xs mt-1" style={{ color: 'var(--pg-text-muted)' }}>Убедитесь, что расширение pg_stat_statements включено</p>
                    </div>
                )}
              </>
          )}
        </div>
      </div>
  );
}