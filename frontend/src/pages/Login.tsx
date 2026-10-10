import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { apiClient } from '@/api/client';

export function Login() {
const [form, setForm] = useState({ username: '', password: '' });
const [error, setError] = useState('');
const navigate = useNavigate();

const handleSubmit = async (e: React.FormEvent) => {
e.preventDefault();
setError('');
try {
  const res = await apiClient.post('/api/auth/login', form);
  localStorage.setItem('user', JSON.stringify(res.data));
  navigate('/');
} catch (err: any) {
  setError(err.response?.data?.message || 'Login failed');
}
};

return (
<div className="flex justify-center items-center min-h-screen bg-slate-900 text-white p-4">
  <form onSubmit={handleSubmit} className="p-8 bg-slate-800 rounded-xl shadow-xl w-full max-w-md space-y-4 border border-slate-700">
    <h2 className="text-2xl font-bold tracking-tight">Welcome Back</h2>
    <p className="text-sm text-slate-400">Sign in to your TestForge account</p>

    {error && <div className="p-3 bg-rose-950 border border-rose-500/50 text-rose-300 text-sm rounded">{error}</div>}

    <div className="space-y-1">
      <label className="block text-sm font-medium text-slate-300">Username</label>
      <input
        type="text"
        className="w-full p-2.5 rounded-lg bg-slate-700 border border-slate-600 text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        value={form.username}
        onChange={(e) => setForm({ ...form, username: e.target.value })}
        required
      />
    </div>

    <div className="space-y-1">
      <label className="block text-sm font-medium text-slate-300">Password</label>
      <input
        type="password"
        className="w-full p-2.5 rounded-lg bg-slate-700 border border-slate-600 text-white focus:outline-none focus:ring-2 focus:ring-blue-500"
        value={form.password}
        onChange={(e) => setForm({ ...form, password: e.target.value })}
        required
      />
    </div>

    <button type="submit" className="w-full py-2.5 bg-blue-600 hover:bg-blue-500 text-white rounded-lg font-semibold transition-colors">
      Sign In
    </button>

    <p className="text-center text-sm text-slate-400 mt-4">
      Don't have an account?{' '}
      <Link to="/register" className="text-blue-400 hover:underline">
        Register here
      </Link>
    </p>
  </form>
</div>
);
}