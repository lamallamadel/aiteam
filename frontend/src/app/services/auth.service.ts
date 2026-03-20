import { Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, BehaviorSubject, throwError, of } from 'rxjs';
import { tap, catchError, switchMap, map } from 'rxjs/operators';

export interface LoginRequest {
    username: string;
    password: string;
}

export interface LoginResponse {
    accessToken?: string;
    refreshToken?: string;
    mfaRequired?: boolean;
    mfaToken?: string;
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

    /** CSRF token from API response body (reliable when cookie is not set due to proxy/origin). */
    private csrfTokenFromApi: string | null = null;
    
    private isRefreshing = false;
    private refreshTokenSubject = new BehaviorSubject<string | null>(null);
    
    refreshing$ = new BehaviorSubject<boolean>(false);
    
    currentUser = signal<UserProfile | null>(null);

    constructor(
        private http: HttpClient,
        private router: Router
    ) {
        if (this.hasToken()) {
            this.loadUserProfile().subscribe();
        }
    }

    login(username: string, password: string): Observable<LoginResponse> {
        const request: LoginRequest = { username, password };
        return this.http.post<LoginResponse>('/api/auth/login', request).pipe(
            tap(response => {
                if (!response.mfaRequired && response.accessToken && response.refreshToken) {
                    this.storeTokens(response.accessToken, response.refreshToken);
                }
            }),
            switchMap(response => {
                if (response.mfaRequired) {
                    return of(response);
                }
                return this.fetchCsrfToken().pipe(
                    switchMap(() => this.loadUserProfile()),
                    switchMap(() => of(response)),
                    catchError(() => {
                        return of(response);
                    })
                );
            }),
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
                this.currentUser.set(null);
                this.router.navigate(['/auth/login']);
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
            map(response => response.accessToken),
            catchError(error => {
                this.clearTokens();
                this.currentUser.set(null);
                this.router.navigate(['/auth/login']);
                return throwError(() => error);
            })
        );
    }

    logout(): Observable<void> {
        const refreshToken = this.getRefreshToken();
        const request: RefreshRequest = { refreshToken: refreshToken || '' };
        
        return this.http.post<void>('/api/auth/logout', request).pipe(
            tap(() => {
                this.clearTokens();
                this.currentUser.set(null);
                this.router.navigate(['/auth/login']);
            }),
            catchError(error => {
                this.clearTokens();
                this.currentUser.set(null);
                this.router.navigate(['/auth/login']);
                return of(undefined);
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

    navigateToHome(): void {
        this.router.navigate(['/dashboard']);
    }

    storeTokens(accessToken: string, refreshToken: string): void {
        localStorage.setItem(this.TOKEN_KEY, accessToken);
        localStorage.setItem(this.REFRESH_TOKEN_KEY, refreshToken);
    }

    clearTokens(): void {
        localStorage.removeItem(this.TOKEN_KEY);
        localStorage.removeItem(this.REFRESH_TOKEN_KEY);
        this.csrfTokenFromApi = null;
    }

    /**
     * Réécrit le cookie XSRF-TOKEN sur l’origine du front pour que le navigateur envoie
     * `Cookie: XSRF-TOKEN=…` sur les requêtes mutantes (en plus de l’en-tête X-CSRF-TOKEN).
     * Sans cela, certaines réponses API peuvent faire disparaître le cookie côté jar avant un POST.
     */
    ensureXsrfCookieInBrowser(token: string): void {
        if (!token || /[;\s]/.test(token)) {
            return;
        }
        const secure = typeof location !== 'undefined' && location.protocol === 'https:' ? '; Secure' : '';
        document.cookie = `${this.CSRF_TOKEN_COOKIE}=${token}; Path=/; Max-Age=3600; SameSite=Lax${secure}`;
    }

    fetchCsrfToken(): Observable<void> {
        //this.clearConflictingCookies();
        return this.http.get<{ token: string }>('/api/auth/csrf', {
            withCredentials: true
        }).pipe(
            tap((res) => {
                if (res?.token) {
                    this.csrfTokenFromApi = res.token;
                    this.ensureXsrfCookieInBrowser(res.token);
                }
            }),
            map(() => void 0),
            catchError(() => {
                return throwError(() => new Error('Failed to fetch CSRF token'));
            })
        );
    }

    private clearConflictingCookies(): void {
        // Clear common CSRF cookie names that might conflict (especially from Postman Agent or stale sessions)
        const conflictingNames = ['XSRF-TOKEN', 'X-CSRF-TOKEN', 'CSRF-TOKEN', 'csrf-token', '_csrf'];
        const domain = window.location.hostname;
        const path = '/';

        conflictingNames.forEach(name => {
            // Try clearing without domain/path first
            document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=${path}`;
            document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=${path}; domain=${domain}`;
            
            // Log for debugging if we're in a dev environment
            if (document.cookie.includes(name)) {
                console.warn(`Attempted to clear ${name} but it still exists in document.cookie`);
            }
        });
    }

    getCsrfToken(): string | null {
        // Priority 1: Token from API response (most reliable for proxy scenarios)
        if (this.csrfTokenFromApi) {
            return this.csrfTokenFromApi;
        }

        // Priority 2: XSRF-TOKEN cookie
        const name = this.CSRF_TOKEN_COOKIE + '=';
        const decodedCookie = decodeURIComponent(document.cookie);
        const cookieArray = decodedCookie.split(';');
        
        // Search specifically for XSRF-TOKEN
        for (let cookie of cookieArray) {
            cookie = cookie.trim();
            if (cookie.indexOf(name) === 0) {
                const tokenValue = cookie.substring(name.length);
                // Basic validation: ensure it's not the generic 'CSRF-TOKEN' cookie value if they differ
                return tokenValue;
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
