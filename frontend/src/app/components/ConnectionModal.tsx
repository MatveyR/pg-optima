import { useState, useEffect } from 'react';
import { X, TestTube, Loader2, CheckCircle } from 'lucide-react';

interface ConnectionModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSave: (connection: any) => void;
  connection?: any;
}

export function ConnectionModal({ isOpen, onClose, onSave, connection }: ConnectionModalProps) {
  const [formData, setFormData] = useState({
    name: '',
    host: '',
    port: 5432,
    database: '',
    username: '',
    password: '',
    sslMode: 'prefer',
  });
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<'success' | 'error' | null>(null);

  useEffect(() => {
    if (connection) {
      setFormData({
        name: connection.name || '',
        host: connection.host || '',
        port: connection.port || 5432,
        database: connection.database || '',
        username: connection.username || '',
        password: '',
        sslMode: 'prefer',
      });
    } else {
      setFormData({
        name: '',
        host: '',
        port: 5432,
        database: '',
        username: '',
        password: '',
        sslMode: 'prefer',
      });
    }
    setTestResult(null);
  }, [connection, isOpen]);

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);
    // Simulate API call
    await new Promise((resolve) => setTimeout(resolve, 1500));
    setTestResult('success');
    setTesting(false);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    onSave(formData);
  };

  if (!isOpen) return null;

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center p-4"
      style={{ backgroundColor: 'rgba(0, 0, 0, 0.7)', backdropFilter: 'blur(4px)' }}
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg rounded-lg p-6 shadow-2xl"
        style={{ backgroundColor: 'var(--pg-bg-surface)' }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold" style={{ color: 'var(--pg-text-white)' }}>
            {connection ? 'Edit Connection' : 'New Connection'}
          </h2>
          <button
            onClick={onClose}
            className="p-1 rounded-lg transition-colors"
            style={{ color: 'var(--pg-text-muted)' }}
            onMouseEnter={(e) => {
              e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
              e.currentTarget.style.color = 'var(--pg-text-primary)';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.backgroundColor = 'transparent';
              e.currentTarget.style.color = 'var(--pg-text-muted)';
            }}
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
              Connection Name
            </label>
            <input
              type="text"
              value={formData.name}
              onChange={(e) => setFormData({ ...formData, name: e.target.value })}
              placeholder="My Database"
              required
              className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm"
              style={{
                backgroundColor: 'var(--pg-bg-card)',
                borderColor: 'var(--pg-border)',
                color: 'var(--pg-text-primary)',
              }}
              onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
              onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
            />
          </div>

          <div className="grid grid-cols-3 gap-3">
            <div className="col-span-2">
              <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                Host
              </label>
              <input
                type="text"
                value={formData.host}
                onChange={(e) => setFormData({ ...formData, host: e.target.value })}
                placeholder="localhost"
                required
                className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm font-mono"
                style={{
                  backgroundColor: 'var(--pg-bg-card)',
                  borderColor: 'var(--pg-border)',
                  color: 'var(--pg-text-primary)',
                }}
                onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
                onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
              />
            </div>
            <div>
              <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                Port
              </label>
              <input
                type="number"
                value={formData.port}
                onChange={(e) => setFormData({ ...formData, port: parseInt(e.target.value) })}
                required
                className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm font-mono"
                style={{
                  backgroundColor: 'var(--pg-bg-card)',
                  borderColor: 'var(--pg-border)',
                  color: 'var(--pg-text-primary)',
                }}
                onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
                onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
              />
            </div>
          </div>

          <div>
            <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
              Database Name
            </label>
            <input
              type="text"
              value={formData.database}
              onChange={(e) => setFormData({ ...formData, database: e.target.value })}
              placeholder="postgres"
              required
              className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm font-mono"
              style={{
                backgroundColor: 'var(--pg-bg-card)',
                borderColor: 'var(--pg-border)',
                color: 'var(--pg-text-primary)',
              }}
              onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
              onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
            />
          </div>

          <div>
            <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
              Username
            </label>
            <input
              type="text"
              value={formData.username}
              onChange={(e) => setFormData({ ...formData, username: e.target.value })}
              placeholder="postgres"
              required
              className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm font-mono"
              style={{
                backgroundColor: 'var(--pg-bg-card)',
                borderColor: 'var(--pg-border)',
                color: 'var(--pg-text-primary)',
              }}
              onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
              onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
            />
          </div>

          <div>
            <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
              Password
            </label>
            <input
              type="password"
              value={formData.password}
              onChange={(e) => setFormData({ ...formData, password: e.target.value })}
              placeholder="••••••••"
              className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm"
              style={{
                backgroundColor: 'var(--pg-bg-card)',
                borderColor: 'var(--pg-border)',
                color: 'var(--pg-text-primary)',
              }}
              onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
              onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
            />
          </div>

          <div>
            <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
              SSL Mode
            </label>
            <select
              value={formData.sslMode}
              onChange={(e) => setFormData({ ...formData, sslMode: e.target.value })}
              className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm"
              style={{
                backgroundColor: 'var(--pg-bg-card)',
                borderColor: 'var(--pg-border)',
                color: 'var(--pg-text-primary)',
              }}
              onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
              onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
            >
              <option value="disable">Disable</option>
              <option value="prefer">Prefer</option>
              <option value="require">Require</option>
            </select>
          </div>

          {/* Test Result */}
          {testResult === 'success' && (
            <div
              className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm"
              style={{ backgroundColor: 'rgba(80, 250, 123, 0.1)', color: 'var(--pg-success)' }}
            >
              <CheckCircle className="w-4 h-4" />
              Connection successful!
            </div>
          )}

          {/* Actions */}
          <div className="flex gap-3 pt-2">
            <button
              type="button"
              onClick={handleTestConnection}
              disabled={testing}
              className="px-4 py-2.5 rounded-lg font-medium flex items-center gap-2 transition-colors text-sm"
              style={{
                backgroundColor: 'var(--pg-bg-card)',
                color: 'var(--pg-text-secondary)',
                borderWidth: '1px',
                borderColor: 'var(--pg-border)',
              }}
              onMouseEnter={(e) => {
                if (!testing) {
                  e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                  e.currentTarget.style.color = 'var(--pg-text-primary)';
                }
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)';
                e.currentTarget.style.color = 'var(--pg-text-secondary)';
              }}
            >
              {testing ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Testing...
                </>
              ) : (
                <>
                  <TestTube className="w-4 h-4" />
                  Test Connection
                </>
              )}
            </button>
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2.5 rounded-lg font-medium transition-colors text-sm"
              style={{
                backgroundColor: 'var(--pg-bg-card)',
                color: 'var(--pg-text-secondary)',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                e.currentTarget.style.color = 'var(--pg-text-primary)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)';
                e.currentTarget.style.color = 'var(--pg-text-secondary)';
              }}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="flex-1 px-4 py-2.5 rounded-lg font-medium transition-colors text-sm"
              style={{
                backgroundColor: 'var(--pg-accent)',
                color: 'var(--pg-bg-primary)',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent)')}
            >
              Save Connection
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
