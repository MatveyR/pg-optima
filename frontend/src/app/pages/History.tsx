import { useState } from 'react';
import { Clock, Play, Trash2, FileCode, Database, Search } from 'lucide-react';
import { useNavigate } from 'react-router';

interface QueryHistoryItem {
  id: string;
  query: string;
  connection: string;
  executionTime: number;
  timestamp: Date;
  rowCount: number;
}

export function History() {
  const navigate = useNavigate();
  const [searchQuery, setSearchQuery] = useState('');

  const [history] = useState<QueryHistoryItem[]>([
    {
      id: '1',
      query: 'SELECT * FROM users WHERE created_at > \'2024-01-01\' ORDER BY id DESC LIMIT 100',
      connection: 'Продакшн БД',
      executionTime: 245,
      timestamp: new Date('2026-03-19T14:30:00'),
      rowCount: 100,
    },
    {
      id: '2',
      query: 'UPDATE orders SET status = \'completed\' WHERE id IN (SELECT id FROM orders WHERE paid_at IS NOT NULL)',
      connection: 'БД разработки',
      executionTime: 1240,
      timestamp: new Date('2026-03-19T13:15:00'),
      rowCount: 342,
    },
    {
      id: '3',
      query: 'SELECT u.name, COUNT(o.id) as order_count FROM users u LEFT JOIN orders o ON u.id = o.user_id GROUP BY u.id',
      connection: 'Продакшн БД',
      executionTime: 523,
      timestamp: new Date('2026-03-19T11:45:00'),
      rowCount: 5420,
    },
    {
      id: '4',
      query: 'DELETE FROM sessions WHERE expires_at < NOW() - INTERVAL \'30 days\'',
      connection: 'Стенд',
      executionTime: 89,
      timestamp: new Date('2026-03-19T10:20:00'),
      rowCount: 1523,
    },
    {
      id: '5',
      query: 'CREATE INDEX idx_users_email ON users(email) WHERE email IS NOT NULL',
      connection: 'БД разработки',
      executionTime: 3421,
      timestamp: new Date('2026-03-18T16:30:00'),
      rowCount: 0,
    },
  ]);

  const filteredHistory = history.filter((item) =>
      item.query.toLowerCase().includes(searchQuery.toLowerCase()) ||
      item.connection.toLowerCase().includes(searchQuery.toLowerCase())
  );

  const handleRerun = (query: string) => {
    navigate('/');
  };

  const formatTimestamp = (date: Date) => {
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const hours = Math.floor(diff / (1000 * 60 * 60));
    const days = Math.floor(hours / 24);

    if (days > 0) {
      return `${days} дн. назад`;
    } else if (hours > 0) {
      return `${hours} ч. назад`;
    } else {
      const minutes = Math.floor(diff / (1000 * 60));
      return `${minutes} мин. назад`;
    }
  };

  const getQueryType = (query: string) => {
    const normalized = query.trim().toUpperCase();
    if (normalized.startsWith('SELECT')) return 'SELECT';
    if (normalized.startsWith('INSERT')) return 'INSERT';
    if (normalized.startsWith('UPDATE')) return 'UPDATE';
    if (normalized.startsWith('DELETE')) return 'DELETE';
    if (normalized.startsWith('CREATE')) return 'CREATE';
    if (normalized.startsWith('DROP')) return 'DROP';
    return 'ЗАПРОС';
  };

  const getQueryTypeColor = (type: string) => {
    switch (type) {
      case 'SELECT':
        return 'var(--pg-info)';
      case 'INSERT':
        return 'var(--pg-success)';
      case 'UPDATE':
        return 'var(--pg-warning)';
      case 'DELETE':
        return 'var(--pg-error)';
      case 'CREATE':
        return 'var(--pg-accent)';
      default:
        return 'var(--pg-text-muted)';
    }
  };

  return (
      <div className="p-6 max-w-7xl mx-auto">
        {/* Шапка */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold mb-1" style={{ color: 'var(--pg-text-white)' }}>
            История запросов
          </h1>
          <p className="text-sm" style={{ color: 'var(--pg-text-secondary)' }}>
            Просмотр и повторный запуск предыдущих SQL-запросов
          </p>
        </div>

        {/* Поиск */}
        <div className="mb-6">
          <div className="relative max-w-md">
            <Search
                className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4"
                style={{ color: 'var(--pg-text-muted)' }}
            />
            <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Поиск запросов..."
                className="w-full pl-10 pr-4 py-2.5 rounded-lg border outline-none transition-colors text-sm"
                style={{
                  backgroundColor: 'var(--pg-bg-surface)',
                  borderColor: 'var(--pg-border)',
                  color: 'var(--pg-text-primary)',
                }}
                onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
                onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
            />
          </div>
        </div>

        {/* Список истории */}
        <div className="space-y-3">
          {filteredHistory.map((item) => {
            const queryType = getQueryType(item.query);

            return (
                <div
                    key={item.id}
                    className="rounded-lg border p-4 transition-all"
                    style={{
                      backgroundColor: 'var(--pg-bg-surface)',
                      borderColor: 'var(--pg-border)',
                    }}
                    onMouseEnter={(e) => {
                      e.currentTarget.style.borderColor = 'var(--pg-border-light)';
                      e.currentTarget.style.transform = 'translateX(2px)';
                    }}
                    onMouseLeave={(e) => {
                      e.currentTarget.style.borderColor = 'var(--pg-border)';
                      e.currentTarget.style.transform = 'translateX(0)';
                    }}
                >
                  {/* Заголовок */}
                  <div className="flex items-start justify-between mb-3">
                    <div className="flex items-center gap-3 flex-1">
                      <div
                          className="px-2 py-1 rounded text-xs font-bold"
                          style={{
                            backgroundColor: `${getQueryTypeColor(queryType)}15`,
                            color: getQueryTypeColor(queryType),
                          }}
                      >
                        {queryType}
                      </div>
                      <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                        <Database className="w-3.5 h-3.5" />
                        {item.connection}
                      </div>
                      <div className="flex items-center gap-2 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                        <Clock className="w-3.5 h-3.5" />
                        {formatTimestamp(item.timestamp)}
                      </div>
                    </div>
                    <div className="flex items-center gap-2">
                      <button
                          onClick={() => handleRerun(item.query)}
                          className="p-2 rounded-lg transition-colors"
                          style={{
                            backgroundColor: 'var(--pg-bg-card)',
                            color: 'var(--pg-text-secondary)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                            e.currentTarget.style.color = 'var(--pg-accent)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)';
                            e.currentTarget.style.color = 'var(--pg-text-secondary)';
                          }}
                      >
                        <Play className="w-4 h-4" />
                      </button>
                      <button
                          className="p-2 rounded-lg transition-colors"
                          style={{
                            backgroundColor: 'var(--pg-bg-card)',
                            color: 'var(--pg-text-secondary)',
                          }}
                          onMouseEnter={(e) => {
                            e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                            e.currentTarget.style.color = 'var(--pg-error)';
                          }}
                          onMouseLeave={(e) => {
                            e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)';
                            e.currentTarget.style.color = 'var(--pg-text-secondary)';
                          }}
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </div>

                  {/* Текст запроса */}
                  <div
                      className="rounded-lg p-3 mb-3 overflow-x-auto"
                      style={{
                        backgroundColor: 'var(--pg-bg-editor)',
                      }}
                  >
                    <code
                        className="text-xs font-mono"
                        style={{ color: 'var(--pg-text-secondary)' }}
                    >
                      {item.query}
                    </code>
                  </div>

                  {/* Статистика */}
                  <div className="flex items-center gap-4 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                    <div>
                  <span className="font-medium" style={{ color: 'var(--pg-text-primary)' }}>
                    {item.executionTime}мс
                  </span>{' '}
                      время выполнения
                    </div>
                    <div>
                  <span className="font-medium" style={{ color: 'var(--pg-text-primary)' }}>
                    {item.rowCount.toLocaleString()}
                  </span>{' '}
                      строк затронуто
                    </div>
                  </div>
                </div>
            );
          })}

          {filteredHistory.length === 0 && (
              <div
                  className="text-center py-12 rounded-lg border"
                  style={{
                    backgroundColor: 'var(--pg-bg-surface)',
                    borderColor: 'var(--pg-border)',
                  }}
              >
                <FileCode className="w-12 h-12 mx-auto mb-3" style={{ color: 'var(--pg-text-muted)' }} />
                <p className="text-sm" style={{ color: 'var(--pg-text-muted)' }}>
                  Запросы не найдены
                </p>
              </div>
          )}
        </div>
      </div>
  );
}