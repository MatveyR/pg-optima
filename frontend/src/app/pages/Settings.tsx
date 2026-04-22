import { useState } from 'react';
import { User, Lock, Palette, Code, Save } from 'lucide-react';

export function Settings() {
  const [activeSection, setActiveSection] = useState<'profile' | 'editor' | 'appearance'>('profile');

  const [profileData, setProfileData] = useState({
    name: 'Разработчик',
    email: 'dev@pgoptima.com',
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [editorSettings, setEditorSettings] = useState({
    fontSize: 14,
    tabSize: 2,
    lineNumbers: true,
    minimap: false,
    wordWrap: false,
    autoSave: true,
  });

  const handleSaveProfile = (e: React.FormEvent) => {
    e.preventDefault();
    console.log('Сохранение профиля...');
  };

  const handleSaveEditor = (e: React.FormEvent) => {
    e.preventDefault();
    console.log('Сохранение настроек редактора...');
  };

  const sections = [
    { id: 'profile' as const, label: 'Профиль', icon: User },
    { id: 'editor' as const, label: 'Редактор', icon: Code },
    { id: 'appearance' as const, label: 'Внешний вид', icon: Palette },
  ];

  return (
      <div className="p-6 max-w-7xl mx-auto">
        {/* Шапка */}
        <div className="mb-6">
          <h1 className="text-2xl font-bold mb-1" style={{ color: 'var(--pg-text-white)' }}>
            Настройки
          </h1>
          <p className="text-sm" style={{ color: 'var(--pg-text-secondary)' }}>
            Управление учётной записью и настройками приложения
          </p>
        </div>

        <div className="grid grid-cols-12 gap-6">
          {/* Боковая панель */}
          <div className="col-span-3">
            <div
                className="rounded-lg border p-2"
                style={{
                  backgroundColor: 'var(--pg-bg-surface)',
                  borderColor: 'var(--pg-border)',
                }}
            >
              {sections.map((section) => {
                const Icon = section.icon;
                const isActive = activeSection === section.id;

                return (
                    <button
                        key={section.id}
                        onClick={() => setActiveSection(section.id)}
                        className="w-full flex items-center gap-3 px-3 py-2.5 rounded-lg transition-all text-sm"
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
                      <Icon className="w-4 h-4" />
                      {section.label}
                    </button>
                );
              })}
            </div>
          </div>

          {/* Контент */}
          <div className="col-span-9">
            <div
                className="rounded-lg border p-6"
                style={{
                  backgroundColor: 'var(--pg-bg-surface)',
                  borderColor: 'var(--pg-border)',
                }}
            >
              {/* Секция Профиль */}
              {activeSection === 'profile' && (
                  <div>
                    <h2 className="text-lg font-semibold mb-4" style={{ color: 'var(--pg-text-white)' }}>
                      Настройки профиля
                    </h2>
                    <form onSubmit={handleSaveProfile} className="space-y-4 max-w-2xl">
                      <div>
                        <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                          Имя
                        </label>
                        <input
                            type="text"
                            value={profileData.name}
                            onChange={(e) => setProfileData({ ...profileData, name: e.target.value })}
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
                          Email
                        </label>
                        <input
                            type="email"
                            value={profileData.email}
                            onChange={(e) => setProfileData({ ...profileData, email: e.target.value })}
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

                      <div className="pt-4 border-t" style={{ borderColor: 'var(--pg-border)' }}>
                        <h3 className="text-sm font-semibold mb-4 flex items-center gap-2" style={{ color: 'var(--pg-text-white)' }}>
                          <Lock className="w-4 h-4" />
                          Смена пароля
                        </h3>

                        <div className="space-y-3">
                          <div>
                            <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                              Текущий пароль
                            </label>
                            <input
                                type="password"
                                value={profileData.currentPassword}
                                onChange={(e) => setProfileData({ ...profileData, currentPassword: e.target.value })}
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
                              Новый пароль
                            </label>
                            <input
                                type="password"
                                value={profileData.newPassword}
                                onChange={(e) => setProfileData({ ...profileData, newPassword: e.target.value })}
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
                              Подтверждение нового пароля
                            </label>
                            <input
                                type="password"
                                value={profileData.confirmPassword}
                                onChange={(e) => setProfileData({ ...profileData, confirmPassword: e.target.value })}
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
                        </div>
                      </div>

                      <div className="flex gap-3 pt-4">
                        <button
                            type="submit"
                            className="px-4 py-2.5 rounded-lg font-medium flex items-center gap-2 transition-all text-sm"
                            style={{
                              backgroundColor: 'var(--pg-accent)',
                              color: 'var(--pg-bg-primary)',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent-hover)')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent)')}
                        >
                          <Save className="w-4 h-4" />
                          Сохранить изменения
                        </button>
                      </div>
                    </form>
                  </div>
              )}

              {/* Секция Редактор */}
              {activeSection === 'editor' && (
                  <div>
                    <h2 className="text-lg font-semibold mb-4" style={{ color: 'var(--pg-text-white)' }}>
                      Настройки редактора
                    </h2>
                    <form onSubmit={handleSaveEditor} className="space-y-4 max-w-2xl">
                      <div>
                        <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                          Размер шрифта
                        </label>
                        <input
                            type="number"
                            value={editorSettings.fontSize}
                            onChange={(e) => setEditorSettings({ ...editorSettings, fontSize: parseInt(e.target.value) })}
                            min="10"
                            max="24"
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
                          Размер табуляции
                        </label>
                        <select
                            value={editorSettings.tabSize}
                            onChange={(e) => setEditorSettings({ ...editorSettings, tabSize: parseInt(e.target.value) })}
                            className="w-full px-3 py-2 rounded-lg border outline-none transition-colors text-sm"
                            style={{
                              backgroundColor: 'var(--pg-bg-card)',
                              borderColor: 'var(--pg-border)',
                              color: 'var(--pg-text-primary)',
                            }}
                            onFocus={(e) => (e.currentTarget.style.borderColor = 'var(--pg-accent)')}
                            onBlur={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
                        >
                          <option value="2">2 пробела</option>
                          <option value="4">4 пробела</option>
                          <option value="8">8 пробелов</option>
                        </select>
                      </div>

                      <div className="space-y-3 pt-2">
                        <label className="flex items-center gap-3 cursor-pointer">
                          <input
                              type="checkbox"
                              checked={editorSettings.lineNumbers}
                              onChange={(e) => setEditorSettings({ ...editorSettings, lineNumbers: e.target.checked })}
                              className="w-4 h-4 rounded"
                              style={{ accentColor: 'var(--pg-accent)' }}
                          />
                          <span className="text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                        Показывать номера строк
                      </span>
                        </label>

                        <label className="flex items-center gap-3 cursor-pointer">
                          <input
                              type="checkbox"
                              checked={editorSettings.minimap}
                              onChange={(e) => setEditorSettings({ ...editorSettings, minimap: e.target.checked })}
                              className="w-4 h-4 rounded"
                              style={{ accentColor: 'var(--pg-accent)' }}
                          />
                          <span className="text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                        Показывать мини-карту
                      </span>
                        </label>

                        <label className="flex items-center gap-3 cursor-pointer">
                          <input
                              type="checkbox"
                              checked={editorSettings.wordWrap}
                              onChange={(e) => setEditorSettings({ ...editorSettings, wordWrap: e.target.checked })}
                              className="w-4 h-4 rounded"
                              style={{ accentColor: 'var(--pg-accent)' }}
                          />
                          <span className="text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                        Перенос длинных строк
                      </span>
                        </label>

                        <label className="flex items-center gap-3 cursor-pointer">
                          <input
                              type="checkbox"
                              checked={editorSettings.autoSave}
                              onChange={(e) => setEditorSettings({ ...editorSettings, autoSave: e.target.checked })}
                              className="w-4 h-4 rounded"
                              style={{ accentColor: 'var(--pg-accent)' }}
                          />
                          <span className="text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                        Автосохранение запросов
                      </span>
                        </label>
                      </div>

                      <div className="flex gap-3 pt-4">
                        <button
                            type="submit"
                            className="px-4 py-2.5 rounded-lg font-medium flex items-center gap-2 transition-all text-sm"
                            style={{
                              backgroundColor: 'var(--pg-accent)',
                              color: 'var(--pg-bg-primary)',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent-hover)')}
                            onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent)')}
                        >
                          <Save className="w-4 h-4" />
                          Сохранить настройки
                        </button>
                      </div>
                    </form>
                  </div>
              )}

              {/* Секция Внешний вид */}
              {activeSection === 'appearance' && (
                  <div>
                    <h2 className="text-lg font-semibold mb-4" style={{ color: 'var(--pg-text-white)' }}>
                      Настройки внешнего вида
                    </h2>
                    <div className="space-y-4 max-w-2xl">
                      <div>
                        <label className="block mb-3 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                          Тема оформления
                        </label>
                        <div className="grid grid-cols-3 gap-3">
                          <button
                              className="p-4 rounded-lg border transition-all"
                              style={{
                                backgroundColor: 'var(--pg-bg-card)',
                                borderColor: 'var(--pg-accent)',
                                borderWidth: '2px',
                              }}
                          >
                            <div
                                className="w-full h-20 rounded mb-2"
                                style={{ backgroundColor: 'var(--pg-bg-primary)' }}
                            />
                            <div className="text-sm font-medium" style={{ color: 'var(--pg-text-primary)' }}>
                              Тёмная
                            </div>
                            <div className="text-xs mt-0.5" style={{ color: 'var(--pg-accent)' }}>
                              Активна
                            </div>
                          </button>

                          <button
                              className="p-4 rounded-lg border transition-all opacity-50 cursor-not-allowed"
                              style={{
                                backgroundColor: 'var(--pg-bg-card)',
                                borderColor: 'var(--pg-border)',
                              }}
                              disabled
                          >
                            <div className="w-full h-20 rounded mb-2 bg-white" />
                            <div className="text-sm font-medium" style={{ color: 'var(--pg-text-primary)' }}>
                              Светлая
                            </div>
                            <div className="text-xs mt-0.5" style={{ color: 'var(--pg-text-muted)' }}>
                              Скоро
                            </div>
                          </button>

                          <button
                              className="p-4 rounded-lg border transition-all opacity-50 cursor-not-allowed"
                              style={{
                                backgroundColor: 'var(--pg-bg-card)',
                                borderColor: 'var(--pg-border)',
                              }}
                              disabled
                          >
                            <div
                                className="w-full h-20 rounded mb-2"
                                style={{ background: 'linear-gradient(to bottom, #0D1117 50%, #FFFFFF 50%)' }}
                            />
                            <div className="text-sm font-medium" style={{ color: 'var(--pg-text-primary)' }}>
                              Системная
                            </div>
                            <div className="text-xs mt-0.5" style={{ color: 'var(--pg-text-muted)' }}>
                              Скоро
                            </div>
                          </button>
                        </div>
                      </div>

                      <div className="pt-4">
                        <p className="text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                          PgOptima использует тёмную тему, оптимизированную для разработчиков и администраторов БД в условиях низкой освещённости.
                        </p>
                      </div>
                    </div>
                  </div>
              )}
            </div>
          </div>
        </div>
      </div>
  );
}