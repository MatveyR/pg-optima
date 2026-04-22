import { useState, useEffect } from 'react';
import Editor from '@monaco-editor/react';
import {
    Play,
    Save,
    BarChart3,
    Database,
    ChevronDown,
    Clock,
    Activity,
    AlertTriangle,
    Copy,
    Sparkles,
} from 'lucide-react';
import Split from 'react-split';
import { analyticsApi } from '../../api/analytics';
import { connectionsApi } from '../../api/connections';
import { ConnectionDTO, AnalysisResponse } from '../../types/api.types';

interface QueryResult {
    columns: string[];
    rows: any[][];
    rowCount: number;
    executionTime: number;
}

export function SQLEditor() {
    const [sql, setSql] = useState(`-- Добро пожаловать в SQL-редактор PgOptima
-- Пишите ваши PostgreSQL-запросы здесь

SELECT 
  users.id,
  users.name,
  users.email,
  COUNT(orders.id) as order_count,
  SUM(orders.total) as total_spent
FROM users
LEFT JOIN orders ON users.id = orders.user_id
WHERE users.created_at > '2024-01-01'
GROUP BY users.id, users.name, users.email
ORDER BY total_spent DESC
LIMIT 100;`);

    const [activeTab, setActiveTab] = useState<'results' | 'analysis'>('results');
    const [executing, setExecuting] = useState(false);
    const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);
    const [connections, setConnections] = useState<ConnectionDTO[]>([]);
    const [showConnectionDropdown, setShowConnectionDropdown] = useState(false);

    const [queryResult, setQueryResult] = useState<QueryResult>({
        columns: [],
        rows: [],
        rowCount: 0,
        executionTime: 0,
    });

    const [analysisData, setAnalysisData] = useState<AnalysisResponse | null>(null);

    useEffect(() => {
        loadConnections();
    }, []);

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

    const handleExecute = async () => {
        if (!selectedConnectionId) {
            alert('Выберите подключение');
            return;
        }
        setExecuting(true);
        setActiveTab('results');
        try {
            // Запрос на анализ (без авто-применения)
            const { data } = await analyticsApi.analyzeOnly({
                connectionId: selectedConnectionId,
                query: sql,
            });
            setAnalysisData(data);
            // Результатов выполнения (строк) пока нет, показываем метрики
            setQueryResult({
                columns: [],
                rows: [],
                rowCount: 0,
                executionTime: data.executionTimeMs,
            });
        } catch (error: any) {
            console.error('Analysis failed', error);
            alert(error.response?.data?.message || 'Ошибка анализа запроса');
        } finally {
            setExecuting(false);
        }
    };

    const handleAnalyze = async () => {
        if (!selectedConnectionId) {
            alert('Выберите подключение');
            return;
        }
        setExecuting(true);
        setActiveTab('analysis');
        try {
            const { data } = await analyticsApi.analyzeOnly({
                connectionId: selectedConnectionId,
                query: sql,
            });
            setAnalysisData(data);
        } catch (error) {
            console.error('Analysis failed', error);
        } finally {
            setExecuting(false);
        }
    };

    const handleSave = () => {
        // TODO: реализовать сохранение запроса в проект
        console.log('Save query', sql);
    };

    const getSeverityColor = (severity: string) => {
        switch (severity) {
            case 'high':
                return 'var(--pg-severity-high)';
            case 'medium':
                return 'var(--pg-severity-medium)';
            case 'low':
                return 'var(--pg-severity-low)';
            default:
                return 'var(--pg-text-secondary)';
        }
    };

    const copySuggestion = (suggestion: string) => {
        navigator.clipboard.writeText(suggestion);
    };

    const getConnectionName = () => {
        const conn = connections.find(c => c.id === selectedConnectionId);
        return conn ? conn.name : 'Выберите подключение';
    };

    return (
        <div className="h-screen flex flex-col" style={{ backgroundColor: 'var(--pg-bg-primary)' }}>
            {/* Панель инструментов */}
            <div
                className="flex items-center justify-between px-4 py-3 border-b"
                style={{
                    backgroundColor: 'var(--pg-bg-surface)',
                    borderColor: 'var(--pg-border)',
                }}
            >
                <div className="flex items-center gap-3">
                    {/* Выбор подключения */}
                    <div className="relative">
                        <button
                            onClick={() => setShowConnectionDropdown(!showConnectionDropdown)}
                            className="flex items-center gap-2 px-3 py-2 rounded-lg border transition-colors"
                            style={{
                                backgroundColor: 'var(--pg-bg-card)',
                                borderColor: 'var(--pg-border)',
                                color: 'var(--pg-text-primary)',
                            }}
                            onMouseEnter={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border-light)')}
                            onMouseLeave={(e) => (e.currentTarget.style.borderColor = 'var(--pg-border)')}
                        >
                            <Database className="w-4 h-4" style={{ color: 'var(--pg-accent)' }} />
                            <span className="text-sm font-medium">{getConnectionName()}</span>
                            <ChevronDown className="w-4 h-4" style={{ color: 'var(--pg-text-muted)' }} />
                        </button>

                        {showConnectionDropdown && (
                            <div
                                className="absolute top-full left-0 mt-1 w-64 rounded-lg border shadow-lg overflow-hidden z-10"
                                style={{
                                    backgroundColor: 'var(--pg-bg-card)',
                                    borderColor: 'var(--pg-border)',
                                }}
                            >
                                {connections.map((conn) => (
                                    <button
                                        key={conn.id}
                                        onClick={() => {
                                            setSelectedConnectionId(conn.id);
                                            setShowConnectionDropdown(false);
                                        }}
                                        className="w-full px-3 py-2.5 text-left text-sm transition-colors"
                                        style={{
                                            backgroundColor: conn.id === selectedConnectionId ? 'var(--pg-bg-hover)' : 'transparent',
                                            color: 'var(--pg-text-primary)',
                                        }}
                                        onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)')}
                                        onMouseLeave={(e) =>
                                            (e.currentTarget.style.backgroundColor =
                                                conn.id === selectedConnectionId ? 'var(--pg-bg-hover)' : 'transparent')
                                        }
                                    >
                                        {conn.name}
                                    </button>
                                ))}
                            </div>
                        )}
                    </div>

                    <div className="w-px h-6" style={{ backgroundColor: 'var(--pg-border)' }} />

                    <button
                        onClick={handleExecute}
                        disabled={executing}
                        className="flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-all text-sm"
                        style={{
                            backgroundColor: 'var(--pg-accent)',
                            color: 'var(--pg-bg-primary)',
                        }}
                        onMouseEnter={(e) => !executing && (e.currentTarget.style.backgroundColor = 'var(--pg-accent-hover)')}
                        onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent)')}
                    >
                        <Play className="w-4 h-4" />
                        {executing && activeTab === 'results' ? 'Выполнение...' : 'Выполнить'}
                    </button>

                    <button
                        onClick={handleAnalyze}
                        disabled={executing}
                        className="flex items-center gap-2 px-4 py-2 rounded-lg font-medium transition-colors text-sm border"
                        style={{
                            backgroundColor: 'var(--pg-bg-card)',
                            borderColor: 'var(--pg-border)',
                            color: 'var(--pg-text-secondary)',
                        }}
                        onMouseEnter={(e) => {
                            if (!executing) {
                                e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                                e.currentTarget.style.color = 'var(--pg-text-primary)';
                                e.currentTarget.style.borderColor = 'var(--pg-border-light)';
                            }
                        }}
                        onMouseLeave={(e) => {
                            e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)';
                            e.currentTarget.style.color = 'var(--pg-text-secondary)';
                            e.currentTarget.style.borderColor = 'var(--pg-border)';
                        }}
                    >
                        <BarChart3 className="w-4 h-4" />
                        {executing && activeTab === 'analysis' ? 'Анализ...' : 'Анализ'}
                    </button>

                    <button
                        onClick={handleSave}
                        className="p-2 rounded-lg transition-colors"
                        style={{
                            backgroundColor: 'transparent',
                            color: 'var(--pg-text-secondary)',
                        }}
                        onMouseEnter={(e) => {
                            e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                            e.currentTarget.style.color = 'var(--pg-text-primary)';
                        }}
                        onMouseLeave={(e) => {
                            e.currentTarget.style.backgroundColor = 'transparent';
                            e.currentTarget.style.color = 'var(--pg-text-secondary)';
                        }}
                    >
                        <Save className="w-4 h-4" />
                    </button>
                </div>
            </div>

            {/* Редактор и результаты */}
            <div className="flex-1 overflow-hidden">
                <Split
                    className="flex h-full"
                    sizes={[50, 50]}
                    minSize={300}
                    gutterSize={8}
                    gutterStyle={() => ({
                        backgroundColor: 'var(--pg-border)',
                        cursor: 'col-resize',
                    })}
                >
                    {/* Панель редактора */}
                    <div className="flex flex-col h-full">
                        <div
                            className="px-4 py-2 border-b flex items-center justify-between"
                            style={{
                                backgroundColor: 'var(--pg-bg-surface)',
                                borderColor: 'var(--pg-border)',
                            }}
                        >
              <span className="text-sm font-medium" style={{ color: 'var(--pg-text-secondary)' }}>
                Редактор запросов
              </span>
                        </div>
                        <div className="flex-1" style={{ backgroundColor: 'var(--pg-bg-editor)' }}>
                            <Editor
                                height="100%"
                                defaultLanguage="sql"
                                value={sql}
                                onChange={(value) => setSql(value || '')}
                                theme="vs-dark"
                                options={{
                                    minimap: { enabled: false },
                                    fontSize: 14,
                                    lineNumbers: 'on',
                                    scrollBeyondLastLine: false,
                                    automaticLayout: true,
                                    tabSize: 2,
                                    fontFamily: "'JetBrains Mono', 'Fira Code', monospace",
                                }}
                            />
                        </div>
                    </div>

                    {/* Панель результатов/анализа */}
                    <div className="flex flex-col h-full">
                        <div
                            className="flex items-center border-b"
                            style={{
                                backgroundColor: 'var(--pg-bg-surface)',
                                borderColor: 'var(--pg-border)',
                            }}
                        >
                            <button
                                onClick={() => setActiveTab('results')}
                                className="px-4 py-3 text-sm font-medium transition-colors relative"
                                style={{
                                    color: activeTab === 'results' ? 'var(--pg-accent)' : 'var(--pg-text-secondary)',
                                }}
                            >
                                Результаты
                                {activeTab === 'results' && (
                                    <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ backgroundColor: 'var(--pg-accent)' }} />
                                )}
                            </button>
                            <button
                                onClick={() => setActiveTab('analysis')}
                                className="px-4 py-3 text-sm font-medium transition-colors relative"
                                style={{
                                    color: activeTab === 'analysis' ? 'var(--pg-accent)' : 'var(--pg-text-secondary)',
                                }}
                            >
                                Отчёт об анализе
                                {activeTab === 'analysis' && (
                                    <div className="absolute bottom-0 left-0 right-0 h-0.5" style={{ backgroundColor: 'var(--pg-accent)' }} />
                                )}
                            </button>
                        </div>

                        <div className="flex-1 overflow-auto" style={{ backgroundColor: 'var(--pg-bg-surface)' }}>
                            {activeTab === 'results' ? (
                                <div className="p-4">
                                    <div className="flex items-center gap-4 mb-4 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                                        <div className="flex items-center gap-1.5">
                                            <Clock className="w-3.5 h-3.5" />
                                            {queryResult.executionTime}мс
                                        </div>
                                        <div className="flex items-center gap-1.5">
                                            <Activity className="w-3.5 h-3.5" />
                                            {queryResult.rowCount} строк
                                        </div>
                                    </div>
                                    {analysisData?.optimizedQuery && (
                                        <div className="mb-4">
                                            <div className="text-sm font-medium mb-2" style={{ color: 'var(--pg-text-primary)' }}>
                                                Оптимизированный запрос:
                                            </div>
                                            <pre className="p-3 rounded text-xs font-mono whitespace-pre-wrap" style={{ backgroundColor: 'var(--pg-bg-editor)', color: 'var(--pg-text-secondary)' }}>
                        {analysisData.optimizedQuery}
                      </pre>
                                        </div>
                                    )}
                                    {queryResult.rows.length === 0 && (
                                        <div className="text-center py-12" style={{ color: 'var(--pg-text-muted)' }}>
                                            Нет данных для отображения. Выполните запрос.
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <div className="p-4 space-y-4">
                                    {analysisData ? (
                                        <>
                                            <div className="grid grid-cols-2 gap-3">
                                                <div className="p-4 rounded-lg border" style={{ backgroundColor: 'var(--pg-bg-card)', borderColor: 'var(--pg-border)' }}>
                                                    <div className="text-xs mb-1" style={{ color: 'var(--pg-text-muted)' }}>Время выполнения</div>
                                                    <div className="text-xl font-bold" style={{ color: 'var(--pg-text-white)' }}>{analysisData.executionTimeMs}мс</div>
                                                </div>
                                                <div className="p-4 rounded-lg border" style={{ backgroundColor: 'var(--pg-bg-card)', borderColor: 'var(--pg-border)' }}>
                                                    <div className="text-xs mb-1" style={{ color: 'var(--pg-text-muted)' }}>Ожидаемое улучшение</div>
                                                    <div className="text-xl font-bold" style={{ color: 'var(--pg-text-white)' }}>{analysisData.estimatedImprovementPercent}%</div>
                                                </div>
                                            </div>
                                            <div>
                                                <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--pg-text-white)' }}>Проблемы производительности</h3>
                                                <div className="space-y-3">
                                                    {analysisData.issues.map((issue, idx) => (
                                                        <div key={idx} className="rounded-lg border p-4" style={{ backgroundColor: 'var(--pg-bg-card)', borderColor: 'var(--pg-border)' }}>
                                                            <div className="flex items-start justify-between mb-3">
                                                                <div className="flex items-center gap-2">
                                                                    <AlertTriangle className="w-4 h-4 flex-shrink-0" style={{ color: getSeverityColor(issue.severity) }} />
                                                                    <div>
                                                                        <div className="font-medium text-sm" style={{ color: 'var(--pg-text-white)' }}>{issue.title}</div>
                                                                        <div className="text-xs mt-0.5 uppercase font-medium" style={{ color: getSeverityColor(issue.severity) }}>
                                                                            {issue.severity === 'high' && 'высокий'}
                                                                            {issue.severity === 'medium' && 'средний'}
                                                                            {issue.severity === 'low' && 'низкий'}
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                            </div>
                                                            <p className="text-xs mb-3" style={{ color: 'var(--pg-text-secondary)' }}>{issue.description}</p>
                                                            <div>
                                                                <div className="flex items-center gap-2 mb-2">
                                                                    <Sparkles className="w-3.5 h-3.5" style={{ color: 'var(--pg-accent)' }} />
                                                                    <span className="text-xs font-medium" style={{ color: 'var(--pg-text-primary)' }}>Рекомендуемое исправление</span>
                                                                </div>
                                                                <div className="relative rounded-lg p-3 font-mono text-xs" style={{ backgroundColor: 'var(--pg-bg-editor)', color: 'var(--pg-syntax-keyword)' }}>
                                                                    <button onClick={() => copySuggestion(issue.suggestion)} className="absolute top-2 right-2 p-1.5 rounded transition-colors" style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-text-muted)' }}
                                                                            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)'; e.currentTarget.style.color = 'var(--pg-text-primary)'; }}
                                                                            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)'; e.currentTarget.style.color = 'var(--pg-text-muted)'; }}>
                                                                        <Copy className="w-3.5 h-3.5" />
                                                                    </button>
                                                                    <pre className="overflow-x-auto">{issue.suggestion}</pre>
                                                                </div>
                                                            </div>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        </>
                                    ) : (
                                        <div className="text-center py-12" style={{ color: 'var(--pg-text-muted)' }}>Нажмите «Анализ», чтобы получить рекомендации</div>
                                    )}
                                </div>
                            )}
                        </div>
                    </div>
                </Split>
            </div>
        </div>
    );
}