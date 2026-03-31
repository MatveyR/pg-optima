import { useState } from 'react';
import { useNavigate } from 'react-router';
import { Database, Mail, Lock, ArrowRight } from 'lucide-react';

export function Login() {
  const navigate = useNavigate();
  const [isSignUp, setIsSignUp] = useState(false);
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    // Mock authentication - in real app, this would call an API
    navigate('/');
  };

  return (
    <div
      className="min-h-screen flex items-center justify-center p-4"
      style={{ backgroundColor: 'var(--pg-bg-primary)' }}
    >
      <div className="w-full max-w-md">
        {/* Logo and title */}
        <div className="text-center mb-8">
          <div
            className="w-16 h-16 mx-auto mb-4 rounded-2xl flex items-center justify-center"
            style={{ backgroundColor: 'var(--pg-accent)' }}
          >
            <Database className="w-9 h-9" style={{ color: 'var(--pg-bg-primary)' }} />
          </div>
          <h1 className="text-3xl font-bold mb-2" style={{ color: 'var(--pg-text-white)' }}>
            PgOptima
          </h1>
          <p className="text-sm" style={{ color: 'var(--pg-text-secondary)' }}>
            PostgreSQL Query Performance Analysis
          </p>
        </div>

        {/* Login card */}
        <div
          className="rounded-lg p-8 shadow-lg"
          style={{
            backgroundColor: 'var(--pg-bg-surface)',
            boxShadow: 'var(--pg-shadow-lg)',
          }}
        >
          {/* Tabs */}
          <div className="flex gap-2 mb-6">
            <button
              onClick={() => setIsSignUp(false)}
              className="flex-1 py-2.5 px-4 rounded-lg transition-all font-medium text-sm"
              style={{
                backgroundColor: !isSignUp ? 'var(--pg-accent)' : 'transparent',
                color: !isSignUp ? 'var(--pg-bg-primary)' : 'var(--pg-text-secondary)',
              }}
            >
              Sign In
            </button>
            <button
              onClick={() => setIsSignUp(true)}
              className="flex-1 py-2.5 px-4 rounded-lg transition-all font-medium text-sm"
              style={{
                backgroundColor: isSignUp ? 'var(--pg-accent)' : 'transparent',
                color: isSignUp ? 'var(--pg-bg-primary)' : 'var(--pg-text-secondary)',
              }}
            >
              Create Account
            </button>
          </div>

          {/* Form */}
          <form onSubmit={handleSubmit} className="space-y-4">
            <div>
              <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                Email Address
              </label>
              <div className="relative">
                <Mail
                  className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4"
                  style={{ color: 'var(--pg-text-muted)' }}
                />
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="your@email.com"
                  required
                  className="w-full pl-10 pr-4 py-2.5 rounded-lg border outline-none transition-colors text-sm"
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
                Password
              </label>
              <div className="relative">
                <Lock
                  className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4"
                  style={{ color: 'var(--pg-text-muted)' }}
                />
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="••••••••"
                  required
                  className="w-full pl-10 pr-4 py-2.5 rounded-lg border outline-none transition-colors text-sm"
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

            {isSignUp && (
              <div>
                <label className="block mb-2 text-sm" style={{ color: 'var(--pg-text-primary)' }}>
                  Confirm Password
                </label>
                <div className="relative">
                  <Lock
                    className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4"
                    style={{ color: 'var(--pg-text-muted)' }}
                  />
                  <input
                    type="password"
                    value={confirmPassword}
                    onChange={(e) => setConfirmPassword(e.target.value)}
                    placeholder="••••••••"
                    required
                    className="w-full pl-10 pr-4 py-2.5 rounded-lg border outline-none transition-colors text-sm"
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
            )}

            <button
              type="submit"
              className="w-full py-3 px-4 rounded-lg font-medium transition-all flex items-center justify-center gap-2 mt-6"
              style={{
                backgroundColor: 'var(--pg-accent)',
                color: 'var(--pg-bg-primary)',
              }}
              onMouseEnter={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent-hover)')}
              onMouseLeave={(e) => (e.currentTarget.style.backgroundColor = 'var(--pg-accent)')}
            >
              {isSignUp ? 'Create Account' : 'Sign In'}
              <ArrowRight className="w-4 h-4" />
            </button>
          </form>

          {!isSignUp && (
            <div className="mt-4 text-center">
              <a
                href="#"
                className="text-sm transition-colors"
                style={{ color: 'var(--pg-accent)' }}
                onMouseEnter={(e) => (e.currentTarget.style.color = 'var(--pg-accent-hover)')}
                onMouseLeave={(e) => (e.currentTarget.style.color = 'var(--pg-accent)')}
              >
                Forgot password?
              </a>
            </div>
          )}
        </div>

        <p className="text-center mt-6 text-xs" style={{ color: 'var(--pg-text-muted)' }}>
          By continuing, you agree to our Terms of Service and Privacy Policy
        </p>
      </div>
    </div>
  );
}
