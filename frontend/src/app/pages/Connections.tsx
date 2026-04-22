import { useState, useEffect } from 'react';
import { Plus, Database, Edit, Trash2, CheckCircle, XCircle, TestTube } from 'lucide-react';
import { ConnectionModal } from '../components/ConnectionModal';
import { connectionsApi } from '../../api/connections';
import { ConnectionDTO, CreateConnectionRequest } from '../../types/api.types';

export function Connections() {
  const [connections, setConnections] = useState<ConnectionDTO[]>([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingConnection, setEditingConnection] = useState<ConnectionDTO | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    loadConnections();
  }, []);

  const loadConnections = async () => {
    try {
      const { data } = await connectionsApi.getAll();
      setConnections(data);
    } catch (error) {
      console.error('Failed to load connections', error);
    } finally {
      setLoading(false);
    }
  };

  const handleAddConnection = () => {
    setEditingConnection(null);
    setIsModalOpen(true);
  };

  const handleEditConnection = (connection: ConnectionDTO) => {
    setEditingConnection(connection);
    setIsModalOpen(true);
  };

  const handleDeleteConnection = async (id: number) => {
    if (confirm('Удалить подключение?')) {
      try {
        await connectionsApi.delete(id);
        setConnections(connections.filter((c) => c.id !== id));
      } catch (error) {
        console.error('Delete failed', error);
      }
    }
  };

  const handleSaveConnection = async (connectionData: CreateConnectionRequest) => {
    try {
      if (editingConnection) {
        const { data } = await connectionsApi.update(editingConnection.id, connectionData);
        setConnections(connections.map((c) => (c.id === data.id ? data : c)));
      } else {
        const { data } = await connectionsApi.create(connectionData);
        setConnections([...connections, data]);
      }
      setIsModalOpen(false);
    } catch (error) {
      console.error('Save failed', error);
    }
  };

  return (
      <div className="p-6 max-w-7xl mx-auto">
        {/* Шапка */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h1 className="text-2xl font-bold mb-1" style={{ color: 'var(--pg-text-white)' }}>
              Подключения к БД
            </h1>
            <p className="text-sm" style={{ color: 'var(--pg-text-secondary)' }}>
              Управление подключениями к PostgreSQL
            </p>
          </div>
          <button
              onClick={handleAddConnection}
              className="px-4 py-2.5 rounded-lg font-medium flex items-center gap-2 transition-all shadow-md"
              style={{
                backgroundColor: 'var(--pg-accent)',
                color: 'var(--pg-bg-primary)',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--pg-accent-hover)';
                e.currentTarget.style.transform = 'translateY(-1px)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--pg-accent)';
                e.currentTarget.style.transform = 'translateY(0)';
              }}
          >
            <Plus className="w-4 h-4" />
            Новое подключение
          </button>
        </div>

        {/* Сетка подключений */}
        {loading ? (
            <div className="text-center py-12" style={{ color: 'var(--pg-text-secondary)' }}>
              Загрузка...
            </div>
        ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
              {connections.map((connection) => (
                  <div
                      key={connection.id}
                      className="rounded-lg p-5 border transition-all"
                      style={{
                        backgroundColor: 'var(--pg-bg-surface)',
                        borderColor: 'var(--pg-border)',
                        boxShadow: 'var(--pg-shadow-sm)',
                      }}
                      onMouseEnter={(e) => {
                        e.currentTarget.style.borderColor = 'var(--pg-border-light)';
                        e.currentTarget.style.transform = 'translateY(-2px)';
                        e.currentTarget.style.boxShadow = 'var(--pg-shadow-md)';
                      }}
                      onMouseLeave={(e) => {
                        e.currentTarget.style.borderColor = 'var(--pg-border)';
                        e.currentTarget.style.transform = 'translateY(0)';
                        e.currentTarget.style.boxShadow = 'var(--pg-shadow-sm)';
                      }}
                  >
                    {/* Заголовок */}
                    <div className="flex items-start justify-between mb-4">
                      <div className="flex items-center gap-3">
                        <div
                            className="w-10 h-10 rounded-lg flex items-center justify-center"
                            style={{ backgroundColor: 'var(--pg-bg-card)' }}
                        >
                          <Database className="w-5 h-5" style={{ color: 'var(--pg-accent)' }} />
                        </div>
                        <div>
                          <h3 className="font-semibold" style={{ color: 'var(--pg-text-white)' }}>
                            {connection.name}
                          </h3>
                          {/* Статус всегда "неизвестен", т.к. бекенд его не хранит */}
                          <div className="flex items-center gap-1.5 mt-1">
                            <XCircle className="w-3.5 h-3.5" style={{ color: 'var(--pg-text-muted)' }} />
                            <span className="text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                        Не проверено
                      </span>
                          </div>
                        </div>
                      </div>
                    </div>

                    {/* Детали */}
                    <div className="space-y-2 mb-4">
                      <div className="flex justify-between text-xs">
                        <span style={{ color: 'var(--pg-text-muted)' }}>Хост:</span>
                        <span className="font-mono" style={{ color: 'var(--pg-text-secondary)' }}>
                    {connection.host}:{connection.port}
                  </span>
                      </div>
                      <div className="flex justify-between text-xs">
                        <span style={{ color: 'var(--pg-text-muted)' }}>БД:</span>
                        <span className="font-mono" style={{ color: 'var(--pg-text-secondary)' }}>
                    {connection.database}
                  </span>
                      </div>
                      <div className="flex justify-between text-xs">
                        <span style={{ color: 'var(--pg-text-muted)' }}>Пользователь:</span>
                        <span className="font-mono" style={{ color: 'var(--pg-text-secondary)' }}>
                    {connection.username}
                  </span>
                      </div>
                    </div>

                    {/* Действия */}
                    <div className="flex gap-2 pt-4 border-t" style={{ borderColor: 'var(--pg-border)' }}>
                      <button
                          className="flex-1 px-3 py-2 rounded-lg text-xs font-medium transition-colors flex items-center justify-center gap-1.5"
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
                          onClick={async () => {
                            // Тестирование требует пароль – в текущем DTO его нет.
                            // Лучше открыть модалку с тестом или запросить пароль отдельно.
                            alert('Тестирование доступно при редактировании подключения');
                          }}
                      >
                        <TestTube className="w-3.5 h-3.5" />
                        Тест
                      </button>
                      <button
                          onClick={() => handleEditConnection(connection)}
                          className="flex-1 px-3 py-2 rounded-lg text-xs font-medium transition-colors flex items-center justify-center gap-1.5"
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
                        <Edit className="w-3.5 h-3.5" />
                        Изменить
                      </button>
                      <button
                          onClick={() => handleDeleteConnection(connection.id)}
                          className="px-3 py-2 rounded-lg text-xs font-medium transition-colors"
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
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </div>
              ))}
            </div>
        )}

        {/* Модальное окно подключения */}
        <ConnectionModal
            isOpen={isModalOpen}
            onClose={() => setIsModalOpen(false)}
            onSave={handleSaveConnection}
            connection={editingConnection}
        />
      </div>
  );
}