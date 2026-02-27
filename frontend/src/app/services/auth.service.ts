import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, BehaviorSubject, throwError, of } from 'rxjs';
import { tap, catchError, switchMap } from 'rxjs/operators';

export interface LoginRequest {
    username: string;
    password: string;
}

export interface LoginResponse {
    accessToken: string;
    refreshToken: string;
    tokenType?: string;
    expiresIn?: number;
}

export interface RefreshRequest {
    refreshToken: string;
}

export interface RefreshResponse {
    accessToken: string;
    refreshToken: string;
    tokenType?: string;
    expiresIn?: number;
}

export interface UserProfile {
    id: string;
    username: string;
    email: string;
    roles: string[];
    firstName?: string;
    lastName?: string;
    avatar?: string;
}

@Injectable({
    providedIn: 'root'
})
export class AuthService {
    private readonly TOKEN_KEY = 'atlasia_access_token';
    private readonly REFRESH_TOKEN_KEY = 'atlasia_refresh_token';
    private readonly CSRF_TOKEN_COOKIE = 'XSRF-TOKEN';
    
    private isRefreshing = false;
    private refreshTokenSubject = new BehaviorSubject<string | null>(null);
    
    refreshing$ = new BehaviorSubject<boolean>(false);
    
    currentUser = signal<UserProfile | null>(null);

    constructor(
        private http: HttpClient,
        private router: Router
    ) {
        this.loadUserProfile();
    }

    login(username: string, password: string): Observable<LoginResponse> {
        const request: LoginRequest = { username, password };
        return this.http.post<LoginResponse>('/api/auth/login', request).pipe(
            tap(response => {
                this.storeTokens(response.accessToken, response.refreshToken);
            }),
            switchMap(response => this.loadUserProfile().pipe(
                switchMap(() => of(response))
            )),
            catchError(this.handleError)
        );
    }

    refreshToken(): Observable<RefreshResponse> {
        const refreshToken = this.getRefreshToken();
        if (!refreshToken) {
            return throwError(() => new Error('No refresh token available'));
        }

        const request: RefreshRequest = { refreshToken };
        return this.http.post<RefreshResponse>('/api/auth/refresh', request).pipe(
            tap(response => {
                this.storeTokens(response.accessToken, response.refreshToken);
            }),
            catchError(error => {
                this.clearTokens();
                this.router.navigate(['/onboarding']);
                return throwError(() => error);
            })
        );
    }

    refreshAccessToken(): Observable<string> {
        const refreshToken = this.getRefreshToken();
        if (!refreshToken) {
            return throwError(() => new Error('No refresh token available'));
        }

        const request: RefreshRequest = { refreshToken };
        return this.http.post<RefreshResponse>('/api/auth/refresh', request).pipe(
            tap(response => {
                this.storeTokens(response.accessToken, response.refreshToken);
            }),
            switchMap(response => [response.accessToken]),
            catchError(error => {
                this.clearTokens();
                this.router.navigate(['/onboarding']);
                return throwError(() => error);
            })
        );
    }

    logout(): Observable<void> {
        return this.http.post<void>('/api/auth/logout', {}).pipe(
            tap(() => {
                this.clearTokens();
                this.currentUser.set(null);
                this.router.navigate(['/onboarding']);
            }),
            catchError(error => {
                this.clearTokens();
                this.currentUser.set(null);
                this.router.navigate(['/onboarding']);
                return throwError(() => error);
            })
        );
    }

    getUserProfile(): Observable<UserProfile> {
        return this.http.get<UserProfile>('/api/auth/me').pipe(
            tap(user => this.currentUser.set(user)),
            catchError(this.handleError)
        );
    }

    setAccessToken(token: string): void {
        localStorage.setItem(this.TOKEN_KEY, token);
    }

    getAccessToken(): string | null {
        return localStorage.getItem(this.TOKEN_KEY);
    }

    getRefreshToken(): string | null {
        return localStorage.getItem(this.REFRESH_TOKEN_KEY);
    }

    hasToken(): boolean {
        return !!this.getAccessToken();
    }

    storeTokens(accessToken: string, refreshToken: string): void {
        localStorage.setItem(this.TOKEN_KEY, accessToken);
        localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    }

    clearTokens(): void {
        localStorage.removeItem(this.TOKEN_KEY);
        localStorage.removeItem(this.REFRESH_TOKEN_KEY);
    }

    fetchCsrfToken(): Observable<void> {
        return this.http.get<void>('/api/auth/csrf', {
            withCredentials: true
        }).pipe(
            catchError(() => {
                return throwError(() => new Error('Failed to fetch CSRF token'));
            })
        );
    }

    getCsrfToken(): string | null {
        const name = this.CSRF_TOKEN_COOKIE + '=';
        const decodedCookie = decodeURIComponent(document.cookie);
        const cookieArray = decodedCookie.split(';');
        
        for (let cookie of cookieArray) {
            cookie = cookie.trim();
            if (cookie.indexOf(name) === 0) {
                return cookie.substring(name.length);
            }
        }
        return null;
    }

    getIsRefreshing(): boolean {
        return this.isRefreshing;
    }

    setIsRefreshing(value: boolean): void {
        this.isRefreshing = value;
    }

    getRefreshTokenSubject(): BehaviorSubject<string | null> {
        return this.refreshTokenSubject;
    }

    isAuthenticated(): boolean {
        return this.hasToken() && this.currentUser() !== null;
    }

    loadUserProfile(): Observable<UserProfile | null> {
        if (this.hasToken()) {
            return this.getUserProfile().pipe(
                tap(user => this.currentUser.set(user)),
                catchError(() => {
                    this.currentUser.set(null);
                    return of(null);
                })
            );
        }
        return of(null);
    }

    private handleError(error: HttpErrorResponse): Observable<never> {
        let errorMessage = 'An error occurred';
        if (error.error instanceof ErrorEvent) {
            errorMessage = `Error: ${error.error.message}`;
        } else {
            errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
        }
        return throwError(() => new Error(errorMessage));
    }
}
