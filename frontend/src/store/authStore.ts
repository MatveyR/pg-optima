import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { UserDTO } from '../types/api.types';

interface AuthState {
    accessToken: string | null;
    refreshToken: string | null;
    user: UserDTO | null;
    setTokens: (accessToken: string, refreshToken: string) => void;
    setUser: (user: UserDTO) => void;
    clearTokens: () => void;
}

export const useAuthStore = create<AuthState>()(
    persist(
        (set) => ({
            accessToken: null,
            refreshToken: null,
            user: null,
            setTokens: (accessToken, refreshToken) => set({ accessToken, refreshToken }),
            setUser: (user) => set({ user }),
            clearTokens: () => set({ accessToken: null, refreshToken: null, user: null }),
        }),
        { name: 'auth-storage' }
    )
);