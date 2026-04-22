import { Link, useLocation, useNavigate } from 'react-router';
import { Database, FileCode, History, Settings, LogOut, ChevronLeft, ChevronRight } from 'lucide-react';
import { useState } from 'react';

export function Sidebar() {
  const location = useLocation();
  const navigate = useNavigate();
  const [collapsed, setCollapsed] = useState(false);

  const navItems = [
    { path: '/', label: 'Редактор SQL', icon: FileCode },
    { path: '/connections', label: 'Подключения', icon: Database },
    { path: '/history', label: 'История', icon: History },
    { path: '/settings', label: 'Настройки', icon: Settings },
  ];

  const handleLogout = () => {
    navigate('/login');
  };

  return (
      <div
          className="flex flex-col h-screen transition-all duration-300 border-r"
          style={{
            width: collapsed ? '64px' : '240px',
            backgroundColor: 'var(--pg-bg-surface)',
            borderColor: 'var(--pg-border)',
          }}
      >
        {/* Шапка */}
        <div className="flex items-center justify-between p-4 border-b" style={{ borderColor: 'var(--pg-border)' }}>
          {!collapsed && (
              <div className="flex items-center gap-2">
                <div
                    className="w-8 h-8 rounded-lg flex items-center justify-center"
                    style={{ backgroundColor: 'var(--pg-accent)' }}
                >
                  <Database className="w-5 h-5" style={{ color: 'var(--pg-bg-primary)' }} />
                </div>
                <h1 className="font-semibold" style={{ color: 'var(--pg-text-white)', fontSize: '1.125rem' }}>
                  PgOptima
                </h1>
              </div>
          )}
          <button
              onClick={() => setCollapsed(!collapsed)}
              className="p-1.5 rounded-lg transition-colors hover:bg-opacity-10"
              style={{
                backgroundColor: 'transparent',
                color: 'var(--pg-text-secondary)',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'transparent')}
          >
            {collapsed ? <ChevronRight className="w-4 h-4" /> : <ChevronLeft className="w-4 h-4" />}
          </button>
        </div>

        {/* Навигация */}
        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = location.pathname === item.path;

            return (
                <Link
                    key={item.path}
                    to={item.path}
                    className="flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all"
                    style={{
                      backgroundColor: isActive ? 'var(--pg-accent)' : 'transparent',
                      color: isActive ? 'var(--pg-bg-primary)' : 'var(--pg-text-secondary)',
                    }}
                    onMouseEnter={(e) => {
                      if (!isActive) {
                        e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                        e.currentTarget.style.color = 'var(--pg-text-primary)';
                      }
                    }}
                    onMouseLeave={(e) => {
                      if (!isActive) {
                        e.currentTarget.style.backgroundColor = 'transparent';
                        e.currentTarget.style.color = 'var(--pg-text-secondary)';
                      }
                    }}
                >
                  <Icon className="w-5 h-5 flex-shrink-0" />
                  {!collapsed && <span className="font-medium text-sm">{item.label}</span>}
                </Link>
            );
          })}
        </nav>

        {/* Блок пользователя */}
        <div className="p-3 border-t" style={{ borderColor: 'var(--pg-border)' }}>
          {!collapsed ? (
              <div className="flex items-center gap-3 mb-3 px-3 py-2">
                <div
                    className="w-8 h-8 rounded-full flex items-center justify-center"
                    style={{ backgroundColor: 'var(--pg-primary)' }}
                >
              <span className="font-medium text-xs" style={{ color: 'var(--pg-text-white)' }}>
                РВ
              </span>
                </div>
                <div className="flex-1 overflow-hidden">
                  <div className="text-sm font-medium truncate" style={{ color: 'var(--pg-text-primary)' }}>
                    Разработчик
                  </div>
                  <div className="text-xs truncate" style={{ color: 'var(--pg-text-muted)' }}>
                    dev@pgoptima.com
                  </div>
                </div>
              </div>
          ) : (
              <div className="flex justify-center mb-3">
                <div
                    className="w-8 h-8 rounded-full flex items-center justify-center"
                    style={{ backgroundColor: 'var(--pg-primary)' }}
                >
              <span className="font-medium text-xs" style={{ color: 'var(--pg-text-white)' }}>
                РВ
              </span>
                </div>
              </div>
          )}

          <button
              onClick={handleLogout}
              className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-colors"
              style={{
                backgroundColor: 'transparent',
                color: 'var(--pg-text-secondary)',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                e.currentTarget.style.color = 'var(--pg-error)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.backgroundColor = 'transparent';
                e.currentTarget.style.color = 'var(--pg-text-secondary)';
              }}
          >
            <LogOut className="w-5 h-5 flex-shrink-0" />
            {!collapsed && <span className="font-medium text-sm">Выйти</span>}
          </button>
        </div>
      </div>
  );
}