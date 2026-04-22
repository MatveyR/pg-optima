import { Navigate, Outlet } from 'react-router';
import { useAuthStore } from '../../store/authStore';

export function ProtectedRoute() {
    const accessToken = useAuthStore((state) => state.accessToken);
    const isAuthenticated = !!accessToken;

    return isAuthenticated ? <Outlet /> : <Navigate to="/login" replace />;
}