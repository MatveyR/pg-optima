import { useState, useRef } from 'react';
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
  CheckCircle,
  Copy,
  Sparkles,
} from 'lucide-react';
import Split from 'react-split';

interface QueryResult {
  columns: string[];
  rows: any[][];
  rowCount: number;
  executionTime: number;
}

interface Issue {
  id: string;
  severity: 'high' | 'medium' | 'low';
  title: string;
  description: string;
  suggestion: string;
  improvementPercent?: number;
}

export function SQLEditor() {
  const [sql, setSql] = useState(`-- Welcome to PgOptima SQL Editor
-- Write your PostgreSQL queries here

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
  const [selectedConnection, setSelectedConnection] = useState('Production Database');
  const [showConnectionDropdown, setShowConnectionDropdown] = useState(false);
  
  const [queryResult, setQueryResult] = useState<QueryResult>({
    columns: ['id', 'name', 'email', 'order_count', 'total_spent'],
    rows: [
      [1, 'John Doe', 'john@example.com', 42, 5240.50],
      [2, 'Jane Smith', 'jane@example.com', 38, 4120.75],
      [3, 'Bob Johnson', 'bob@example.com', 29, 3450.00],
      [4, 'Alice Brown', 'alice@example.com', 25, 2890.25],
      [5, 'Charlie Wilson', 'charlie@example.com', 22, 2340.80],
    ],
    rowCount: 5,
    executionTime: 245,
  });

  const [analysisData, setAnalysisData] = useState({
    executionTime: 245,
    rowsScanned: 15420,
    buffersHit: 1234,
    buffersMiss: 45,
    issues: [
      {
        id: '1',
        severity: 'high' as const,
        title: 'Sequential Scan on users table',
        description: 'The query is performing a full table scan on the users table, which can be slow for large datasets.',
        suggestion: 'CREATE INDEX idx_users_created_at ON users(created_at);',
        improvementPercent: 75,
      },
      {
        id: '2',
        severity: 'medium' as const,
        title: 'Missing index on orders.user_id',
        description: 'The LEFT JOIN operation could benefit from an index on the orders.user_id column.',
        suggestion: 'CREATE INDEX idx_orders_user_id ON orders(user_id);',
        improvementPercent: 45,
      },
      {
        id: '3',
        severity: 'low' as const,
        title: 'Consider using EXPLAIN ANALYZE',
        description: 'For more detailed performance insights, run EXPLAIN ANALYZE before your query.',
        suggestion: 'EXPLAIN ANALYZE\nSELECT ...',
        improvementPercent: undefined,
      },
    ] as Issue[],
  });

  const connections = ['Production Database', 'Development Database', 'Staging Environment'];

  const handleExecute = async () => {
    setExecuting(true);
    setActiveTab('results');
    // Simulate query execution
    await new Promise((resolve) => setTimeout(resolve, 1500));
    setExecuting(false);
  };

  const handleAnalyze = async () => {
    setExecuting(true);
    setActiveTab('analysis');
    // Simulate analysis
    await new Promise((resolve) => setTimeout(resolve, 2000));
    setExecuting(false);
  };

  const handleSave = () => {
    console.log('Saving query...');
  };

  const handleFormat = () => {
    // In a real app, this would format the SQL
    console.log('Formatting SQL...');
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

  return (
    <div className="h-screen flex flex-col" style={{ backgroundColor: 'var(--pg-bg-primary)' }}>
      {/* Toolbar */}
      <div
        className="flex items-center justify-between px-4 py-3 border-b"
        style={{
          backgroundColor: 'var(--pg-bg-surface)',
          borderColor: 'var(--pg-border)',
        }}
      >
        <div className="flex items-center gap-3">
          {/* Connection selector */}
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
              <span className="text-sm font-medium">{selectedConnection}</span>
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
                    key={conn}
                    onClick={() => {
                      setSelectedConnection(conn);
                      setShowConnectionDropdown(false);
                    }}
                    className="w-full px-3 py-2.5 text-left text-sm transition-colors"
                    style={{
                      backgroundColor: conn === selectedConnection ? 'var(--pg-bg-hover)' : 'transparent',
                      color: 'var(--pg-text-primary)',
                    }}
                    onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)')}
                    onMouseLeave={(e) =>
                      (e.currentTarget.style.backgroundColor =
                        conn === selectedConnection ? 'var(--pg-bg-hover)' : 'transparent')
                    }
                  >
                    {conn}
                  </button>
                ))}
              </div>
            )}
          </div>

          <div
            className="w-px h-6"
            style={{ backgroundColor: 'var(--pg-border)' }}
          />

          {/* Action buttons */}
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
            {executing && activeTab === 'results' ? 'Executing...' : 'Execute'}
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
            {executing && activeTab === 'analysis' ? 'Analyzing...' : 'Analyze'}
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

      {/* Editor and Results - Split View */}
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
          {/* Editor Panel */}
          <div className="flex flex-col h-full">
            <div
              className="px-4 py-2 border-b flex items-center justify-between"
              style={{
                backgroundColor: 'var(--pg-bg-surface)',
                borderColor: 'var(--pg-border)',
              }}
            >
              <span className="text-sm font-medium" style={{ color: 'var(--pg-text-secondary)' }}>
                Query Editor
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

          {/* Results/Analysis Panel */}
          <div className="flex flex-col h-full">
            {/* Tabs */}
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
                Results
                {activeTab === 'results' && (
                  <div
                    className="absolute bottom-0 left-0 right-0 h-0.5"
                    style={{ backgroundColor: 'var(--pg-accent)' }}
                  />
                )}
              </button>
              <button
                onClick={() => setActiveTab('analysis')}
                className="px-4 py-3 text-sm font-medium transition-colors relative"
                style={{
                  color: activeTab === 'analysis' ? 'var(--pg-accent)' : 'var(--pg-text-secondary)',
                }}
              >
                Analysis Report
                {activeTab === 'analysis' && (
                  <div
                    className="absolute bottom-0 left-0 right-0 h-0.5"
                    style={{ backgroundColor: 'var(--pg-accent)' }}
                  />
                )}
              </button>
            </div>

            {/* Content */}
            <div className="flex-1 overflow-auto" style={{ backgroundColor: 'var(--pg-bg-surface)' }}>
              {activeTab === 'results' ? (
                <div className="p-4">
                  {/* Query info */}
                  <div className="flex items-center gap-4 mb-4 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
                    <div className="flex items-center gap-1.5">
                      <Clock className="w-3.5 h-3.5" />
                      {queryResult.executionTime}ms
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Activity className="w-3.5 h-3.5" />
                      {queryResult.rowCount} rows
                    </div>
                  </div>

                  {/* Results table */}
                  <div
                    className="rounded-lg border overflow-hidden"
                    style={{ borderColor: 'var(--pg-border)' }}
                  >
                    <div className="overflow-x-auto">
                      <table className="w-full text-sm">
                        <thead>
                          <tr style={{ backgroundColor: 'var(--pg-bg-card)' }}>
                            {queryResult.columns.map((col) => (
                              <th
                                key={col}
                                className="px-4 py-3 text-left font-medium text-xs"
                                style={{ color: 'var(--pg-text-primary)' }}
                              >
                                {col}
                              </th>
                            ))}
                          </tr>
                        </thead>
                        <tbody>
                          {queryResult.rows.map((row, i) => (
                            <tr
                              key={i}
                              className="border-t"
                              style={{
                                borderColor: 'var(--pg-border)',
                                backgroundColor: i % 2 === 0 ? 'transparent' : 'var(--pg-bg-card)',
                              }}
                            >
                              {row.map((cell, j) => (
                                <td
                                  key={j}
                                  className="px-4 py-2.5 font-mono text-xs"
                                  style={{ color: 'var(--pg-text-secondary)' }}
                                >
                                  {cell}
                                </td>
                              ))}
                            </tr>
                          ))}
                        </tbody>
                      </table>
                    </div>
                  </div>
                </div>
              ) : (
                <div className="p-4 space-y-4">
                  {/* Summary metrics */}
                  <div className="grid grid-cols-2 gap-3">
                    <div
                      className="p-4 rounded-lg border"
                      style={{
                        backgroundColor: 'var(--pg-bg-card)',
                        borderColor: 'var(--pg-border)',
                      }}
                    >
                      <div className="text-xs mb-1" style={{ color: 'var(--pg-text-muted)' }}>
                        Execution Time
                      </div>
                      <div className="text-xl font-bold" style={{ color: 'var(--pg-text-white)' }}>
                        {analysisData.executionTime}ms
                      </div>
                    </div>
                    <div
                      className="p-4 rounded-lg border"
                      style={{
                        backgroundColor: 'var(--pg-bg-card)',
                        borderColor: 'var(--pg-border)',
                      }}
                    >
                      <div className="text-xs mb-1" style={{ color: 'var(--pg-text-muted)' }}>
                        Rows Scanned
                      </div>
                      <div className="text-xl font-bold" style={{ color: 'var(--pg-text-white)' }}>
                        {analysisData.rowsScanned.toLocaleString()}
                      </div>
                    </div>
                  </div>

                  {/* Issues list */}
                  <div>
                    <h3 className="text-sm font-semibold mb-3" style={{ color: 'var(--pg-text-white)' }}>
                      Performance Issues
                    </h3>
                    <div className="space-y-3">
                      {analysisData.issues.map((issue) => (
                        <div
                          key={issue.id}
                          className="rounded-lg border p-4"
                          style={{
                            backgroundColor: 'var(--pg-bg-card)',
                            borderColor: 'var(--pg-border)',
                          }}
                        >
                          {/* Issue header */}
                          <div className="flex items-start justify-between mb-3">
                            <div className="flex items-center gap-2">
                              <AlertTriangle
                                className="w-4 h-4 flex-shrink-0"
                                style={{ color: getSeverityColor(issue.severity) }}
                              />
                              <div>
                                <div className="font-medium text-sm" style={{ color: 'var(--pg-text-white)' }}>
                                  {issue.title}
                                </div>
                                <div
                                  className="text-xs mt-0.5 uppercase font-medium"
                                  style={{ color: getSeverityColor(issue.severity) }}
                                >
                                  {issue.severity} severity
                                </div>
                              </div>
                            </div>
                            {issue.improvementPercent && (
                              <div
                                className="px-2 py-1 rounded text-xs font-medium"
                                style={{
                                  backgroundColor: 'rgba(80, 250, 123, 0.1)',
                                  color: 'var(--pg-success)',
                                }}
                              >
                                +{issue.improvementPercent}% faster
                              </div>
                            )}
                          </div>

                          {/* Description */}
                          <p className="text-xs mb-3" style={{ color: 'var(--pg-text-secondary)' }}>
                            {issue.description}
                          </p>

                          {/* Suggestion */}
                          <div>
                            <div className="flex items-center gap-2 mb-2">
                              <Sparkles className="w-3.5 h-3.5" style={{ color: 'var(--pg-accent)' }} />
                              <span className="text-xs font-medium" style={{ color: 'var(--pg-text-primary)' }}>
                                Suggested Fix
                              </span>
                            </div>
                            <div
                              className="relative rounded-lg p-3 font-mono text-xs"
                              style={{
                                backgroundColor: 'var(--pg-bg-editor)',
                                color: 'var(--pg-syntax-keyword)',
                              }}
                            >
                              <button
                                onClick={() => copySuggestion(issue.suggestion)}
                                className="absolute top-2 right-2 p-1.5 rounded transition-colors"
                                style={{
                                  backgroundColor: 'var(--pg-bg-card)',
                                  color: 'var(--pg-text-muted)',
                                }}
                                onMouseEnter={(e) => {
                                  e.currentTarget.style.backgroundColor = 'var(--pg-bg-hover)';
                                  e.currentTarget.style.color = 'var(--pg-text-primary)';
                                }}
                                onMouseLeave={(e) => {
                                  e.currentTarget.style.backgroundColor = 'var(--pg-bg-card)';
                                  e.currentTarget.style.color = 'var(--pg-text-muted)';
                                }}
                              >
                                <Copy className="w-3.5 h-3.5" />
                              </button>
                              <pre className="overflow-x-auto">{issue.suggestion}</pre>
                            </div>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>
          </div>
        </Split>
      </div>
    </div>
  );
}
