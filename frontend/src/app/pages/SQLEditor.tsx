import { useState, useEffect } from 'react';
import Editor from '@monaco-editor/react';
import {
    Play,
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
import { ConnectionDTO, AnalysisResponse, ExecuteResponse } from '../../types/api.types';
import {saveQueryToHistory} from "../../store/historyStorage";
import { useLocation } from 'react-router';

export function SQLEditor() {
    const location = useLocation();
    const [sql, setSql] = useState(`-- Добро пожаловать в SQL-редактор PgOptima
-- Начните писать ваши PostgreSQL-запросы здесь
    
SELECT 
    hello
FROM world
`);

    const [activeTab, setActiveTab] = useState<'results' | 'analysis'>('results');
    const [executing, setExecuting] = useState(false);
    const [selectedConnectionId, setSelectedConnectionId] = useState<number | null>(null);
    const [connections, setConnections] = useState<ConnectionDTO[]>([]);
    const [showConnectionDropdown, setShowConnectionDropdown] = useState(false);

    const [queryResult, setQueryResult] = useState<ExecuteResponse | null>(null);
    const [analysisData, setAnalysisData] = useState<AnalysisResponse | null>(null);

    useEffect(() => {
        loadConnections();
        if (location.state) {
            const { initialQuery, initialConnectionId } = location.state as { initialQuery?: string; initialConnectionId?: number };
            if (initialQuery) setSql(initialQuery);
            if (initialConnectionId) setSelectedConnectionId(initialConnectionId);
            window.history.replaceState({}, document.title);
        }
    }, [location.state]);

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
            const { data } = await analyticsApi.execute({
                connectionId: selectedConnectionId,
                query: sql,
            });
            setQueryResult(data);

            if (data.success) {
                const connection = connections.find(c => c.id === selectedConnectionId);
                saveQueryToHistory({
                    query: sql,
                    connectionId: selectedConnectionId,
                    connectionName: connection?.name || 'Неизвестно',
                    executionTimeMs: data.executionTimeMs,
                    rowCount: data.rowCount,
                });
            }
        } catch (error: any) {
            console.error('Execution failed', error);
            setQueryResult({
                success: false,
                errorMessage: error.response?.data?.message || 'Ошибка выполнения запроса',
                columns: [],
                rows: [],
                rowCount: 0,
                executionTimeMs: 0,
            });
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
            const { data } = await analyticsApi.analyze({
                connectionId: selectedConnectionId,
                sqlQuery: sql,
                autoApply: false,
            });
            setAnalysisData(data);
        } catch (error: any) {
            console.error('Analysis failed', error);
            setAnalysisData(null);
            alert(error.response?.data?.message || 'Ошибка анализа запроса');
        } finally {
            setExecuting(false);
        }
    };

    const handleSave = () => {
        console.log('Save query', sql);
    };

    const getImpactColor = (impact: string) => {
        switch (impact) {
            case 'Высокий': return 'var(--pg-severity-high)';
            case 'Средний': return 'var(--pg-severity-medium)';
            case 'Низкий': return 'var(--pg-severity-low)';
            default: return 'var(--pg-text-secondary)';
        }
    };

    const getImpactLabel = (impact: string) => {
        switch (impact) {
            case 'Высокий': return 'HIGH';
            case 'Средний': return 'MEDIUM';
            case 'Низкий': return 'LOW';
            default: return 'INFO';
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
                                    {queryResult && !queryResult.success && (
                                        <div className="mb-4 p-3 rounded bg-red-500/20 text-red-400 text-sm">
                                            {queryResult.errorMessage}
                                        </div>
                                    )}
                                    {queryResult?.success && (
                                        <>
                                            <div className="flex items-center gap-4 mb-4 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                                                <div className="flex items-center gap-1.5">
                                                    <Clock className="w-3.5 h-3.5" />
                                                    {queryResult.executionTimeMs} мс
                                                </div>
                                                <div className="flex items-center gap-1.5">
                                                    <Activity className="w-3.5 h-3.5" />
                                                    {queryResult.rowCount} строк
                                                </div>
                                            </div>
                                            {queryResult.columns.length > 0 ? (
                                                <div className="rounded-lg border overflow-hidden" style={{ borderColor: 'var(--pg-border)' }}>
                                                    <div className="overflow-x-auto">
                                                        <table className="w-full text-sm">
                                                            <thead>
                                                            <tr style={{ backgroundColor: 'var(--pg-bg-card)' }}>
                                                                {queryResult.columns.map((col, idx) => (
                                                                    <th key={idx} className="px-4 py-3 text-left font-medium text-xs" style={{ color: 'var(--pg-text-primary)' }}>
                                                                        {col}
                                                                    </th>
                                                                ))}
                                                            </tr>
                                                            </thead>
                                                            <tbody>
                                                            {queryResult.rows.map((row, i) => (
                                                                <tr key={i} className="border-t" style={{ borderColor: 'var(--pg-border)', backgroundColor: i % 2 === 0 ? 'transparent' : 'var(--pg-bg-card)' }}>
                                                                    {row.map((cell, j) => (
                                                                        <td key={j} className="px-4 py-2.5 font-mono text-xs" style={{ color: 'var(--pg-text-secondary)' }}>
                                                                            {cell !== null ? String(cell) : 'NULL'}
                                                                        </td>
                                                                    ))}
                                                                </tr>
                                                            ))}
                                                            </tbody>
                                                        </table>
                                                    </div>
                                                </div>
                                            ) : (
                                                <div className="text-center py-12" style={{ color: 'var(--pg-text-muted)' }}>
                                                    Запрос выполнен успешно, но не вернул строк.
                                                </div>
                                            )}
                                        </>
                                    )}
                                    {!queryResult && (
                                        <div className="text-center py-12" style={{ color: 'var(--pg-text-muted)' }}>
                                            Нажмите «Выполнить», чтобы увидеть результат.
                                        </div>
                                    )}
                                </div>
                            ) : (
                                <div className="p-4 space-y-4">
                                    {analysisData && analysisData.success ? (
                                        <>
                                            <div className="grid grid-cols-2 gap-3">
                                                <div className="p-4 rounded-lg border" style={{ backgroundColor: 'var(--pg-bg-card)', borderColor: 'var(--pg-border)' }}>
                                                    <div className="text-xs mb-1" style={{ color: 'var(--pg-text-muted)' }}>Время выполнения (ориг.)</div>
                                                    <div className="text-xl font-bold" style={{ color: 'var(--pg-text-white)' }}>
                                                        {analysisData.originalExecutionTimeMs} мс
                                                    </div>
                                                </div>
                                                <div className="p-4 rounded-lg border" style={{ backgroundColor: 'var(--pg-bg-card)', borderColor: 'var(--pg-border)' }}>
                                                    <div className="text-xs mb-1" style={{ color: 'var(--pg-text-muted)' }}>Длительность анализа</div>
                                                    <div className="text-xl font-bold" style={{ color: 'var(--pg-text-white)' }}>
                                                        {analysisData.analysisDurationMs} мс
                                                    </div>
                                                </div>
                                            </div>
                                            <div>
                                                <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--pg-text-white)' }}>
                                                    Рекомендации ({analysisData.recommendations?.length || 0})
                                                </h3>
                                                <div className="space-y-3">
                                                    {analysisData.recommendations?.map((rec, idx) => (
                                                        <div key={idx} className="rounded-lg border p-4" style={{ backgroundColor: 'var(--pg-bg-card)', borderColor: 'var(--pg-border)' }}>
                                                            <div className="flex items-start justify-between mb-3">
                                                                <div className="flex items-center gap-2">
                                                                    <AlertTriangle className="w-4 h-4 flex-shrink-0" style={{ color: getImpactColor(rec.impact) }} />
                                                                    <div>
                                                                        <div className="font-medium text-sm mr-10" style={{ color: 'var(--pg-text-white)' }}>{rec.description}</div>
                                                                        <div className="text-xs mt-0.5 uppercase font-medium" style={{ color: getImpactColor(rec.impact) }}>
                                                                            {getImpactLabel(rec.impact)}
                                                                        </div>
                                                                    </div>
                                                                </div>
                                                                {rec.estimatedImprovement && (
                                                                    <div className="px-2 py-1 rounded text-xs font-medium" style={{ backgroundColor: 'rgba(80, 250, 123, 0.1)', color: 'var(--pg-success)' }}>
                                                                        +{rec.estimatedImprovement}% быстрее
                                                                    </div>
                                                                )}
                                                            </div>
                                                            {rec.sqlCommand && (
                                                                <div>
                                                                    <div className="flex items-center gap-2 mb-2">
                                                                        <Sparkles className="w-3.5 h-3.5" style={{ color: 'var(--pg-accent)' }} />
                                                                        <span className="text-xs font-medium" style={{ color: 'var(--pg-text-primary)' }}>Рекомендуемое исправление</span>
                                                                    </div>
                                                                    <div className="relative rounded-lg p-3 font-mono text-xs" style={{ backgroundColor: 'var(--pg-bg-editor)', color: 'var(--pg-syntax-keyword)' }}>
                                                                        <button
                                                                            onClick={() => copySuggestion(rec.sqlCommand!)}
                                                                            className="absolute top-2 right-2 p-1.5 rounded transition-colors"
                                                                            style={{ backgroundColor: 'var(--pg-bg-card)', color: 'var(--pg-text-muted)' }}
                                                                            onMouseEnter={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)'; e.currentTarget.style.color = 'var(--pg-text-primary)'; }}
                                                                            onMouseLeave={(e) => { e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)'; e.currentTarget.style.color = 'var(--pg-text-muted)'; }}
                                                                        >
                                                                            <Copy className="w-3.5 h-3.5" />
                                                                        </button>
                                                                        <pre className="overflow-x-auto">{rec.sqlCommand}</pre>
                                                                    </div>
                                                                </div>
                                                            )}
                                                        </div>
                                                    ))}
                                                    {(!analysisData.recommendations || analysisData.recommendations.length === 0) && (
                                                        <div className="text-center py-6" style={{ color: 'var(--pg-text-muted)' }}>
                                                            Проблем не найдено. Запрос оптимален.
                                                        </div>
                                                    )}
                                                </div>
                                            </div>
                                        </>
                                    ) : (
                                        <div className="text-center py-12" style={{ color: 'var(--pg-text-muted)' }}>
                                            {analysisData?.errorMessage || 'Нажмите «Анализ», чтобы получить рекомендации.'}
                                        </div>
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