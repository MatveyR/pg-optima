import { useState } from 'react';
import { Plus, Database, Edit, Trash2, CheckCircle, XCircle, TestTube } from 'lucide-react';
import { ConnectionModal } from '../components/ConnectionModal';

interface Connection {
  id: string;
  name: string;
  host: string;
  port: number;
  database: string;
  username: string;
  status: 'connected' | 'disconnected';
}

export function Connections() {
  const [connections, setConnections] = useState<Connection[]>([
    {
      id: '1',
      name: 'Production Database',
      host: 'prod-db.example.com',
      port: 5432,
      database: 'main_db',
      username: 'admin',
      status: 'connected',
    },
    {
      id: '2',
      name: 'Development Database',
      host: 'localhost',
      port: 5432,
      database: 'dev_db',
      username: 'developer',
      status: 'disconnected',
    },
    {
      id: '3',
      name: 'Staging Environment',
      host: 'staging-db.example.com',
      port: 5432,
      database: 'staging_db',
      username: 'admin',
      status: 'connected',
    },
  ]);

  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingConnection, setEditingConnection] = useState<Connection | null>(null);

  const handleAddConnection = () => {
    setEditingConnection(null);
    setIsModalOpen(true);
  };

  const handleEditConnection = (connection: Connection) => {
    setEditingConnection(connection);
    setIsModalOpen(true);
  };

  const handleDeleteConnection = (id: string) => {
    setConnections(connections.filter((c) => c.id !== id));
  };

  const handleSaveConnection = (connection: Partial<Connection>) => {
    if (editingConnection) {
      setConnections(
        connections.map((c) => (c.id === editingConnection.id ? { ...c, ...connection } : c))
      );
    } else {
      setConnections([
        ...connections,
        {
          id: Date.now().toString(),
          status: 'disconnected',
          ...connection,
        } as Connection,
      ]);
    }
    setIsModalOpen(false);
  };

  return (
    <div className="p-6 max-w-7xl mx-auto">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold mb-1" style={{ color: 'var(--pg-text-white)' }}>
            Database Connections
          </h1>
          <p className="text-sm" style={{ color: 'var(--pg-text-secondary)' }}>
            Manage your PostgreSQL database connections
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
          New Connection
        </button>
      </div>

      {/* Connections Grid */}
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
            {/* Header */}
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
                  <div className="flex items-center gap-1.5 mt-1">
                    {connection.status === 'connected' ? (
                      <>
                        <CheckCircle className="w-3.5 h-3.5" style={{ color: 'var(--pg-success)' }} />
                        <span className="text-xs" style={{ color: 'var(--pg-success)' }}>
                          Connected
                        </span>
                      </>
                    ) : (
                      <>
                        <XCircle className="w-3.5 h-3.5" style={{ color: 'var(--pg-text-muted)' }} />
                        <span className="text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                          Disconnected
                        </span>
                      </>
                    )}
                  </div>
                </div>
              </div>
            </div>

            {/* Connection details */}
            <div className="space-y-2 mb-4">
              <div className="flex justify-between text-xs">
                <span style={{ color: 'var(--pg-text-muted)' }}>Host:</span>
                <span className="font-mono" style={{ color: 'var(--pg-text-secondary)' }}>
                  {connection.host}
                </span>
              </div>
              <div className="flex justify-between text-xs">
                <span style={{ color: 'var(--pg-text-muted)' }}>Database:</span>
                <span className="font-mono" style={{ color: 'var(--pg-text-secondary)' }}>
                  {connection.database}
                </span>
              </div>
              <div className="flex justify-between text-xs">
                <span style={{ color: 'var(--pg-text-muted)' }}>User:</span>
                <span className="font-mono" style={{ color: 'var(--pg-text-secondary)' }}>
                  {connection.username}
                </span>
              </div>
            </div>

            {/* Actions */}
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
              >
                <TestTube className="w-3.5 h-3.5" />
                Test
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
                Edit
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

      {/* Connection Modal */}
      <ConnectionModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSave={handleSaveConnection}
        connection={editingConnection}
      />
    </div>
  );
}
