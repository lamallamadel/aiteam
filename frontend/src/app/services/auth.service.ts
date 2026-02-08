import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly TOKEN_KEY = 'atlasia_orchestrator_token';

    setToken(token: string): void {
        localStorage.setItem(this.TOKEN_KEY, token);
    }

    getToken(): string | null {
        return localStorage.getItem(this.TOKEN_KEY);
    }

    hasToken(): boolean {
        return !!this.getToken();
    }

    clearToken(): void {
        localStorage.removeItem(this.TOKEN_KEY);
    }
}
