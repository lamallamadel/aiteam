import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, switchMap, filter, take } from 'rxjs/operators';
import { throwError } from 'rxjs';

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
    const authService = inject(AuthService);
    const token = authService.getAccessToken();

    const headers: { [key: string]: string } = {};

    if (token && !req.url.includes('/api/auth/login') && !req.url.includes('/api/auth/refresh')) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    const method = req.method.toUpperCase();
    if (method === 'POST' || method === 'PUT' || method === 'DELETE' || method === 'PATCH') {
        const csrfToken = authService.getCsrfToken();
        if (csrfToken) {
            headers['X-CSRF-TOKEN'] = csrfToken;
        }
    }

    let clonedReq = req;
    if (Object.keys(headers).length > 0) {
        clonedReq = req.clone({ setHeaders: headers });
    }

    return next(clonedReq).pipe(
        catchError((error: HttpErrorResponse) => {
            if (error.status === 401 && !req.url.includes('/api/auth/login') && !req.url.includes('/api/auth/refresh')) {
                return handle401Error(clonedReq, next, authService);
            }
            return throwError(() => error);
        })
    );
};

function handle401Error(request: HttpRequest<unknown>, next: HttpHandlerFn, authService: AuthService) {
    if (!authService.getIsRefreshing()) {
        authService.setIsRefreshing(true);
        authService.getRefreshTokenSubject().next(null);

        const refreshToken = authService.getRefreshToken();
        if (refreshToken) {
            return authService.refreshToken().pipe(
                switchMap((response) => {
                    authService.setIsRefreshing(false);
                    authService.getRefreshTokenSubject().next(response.accessToken);
                    return next(addTokenToRequest(request, response.accessToken));
                }),
                catchError((err) => {
                    authService.setIsRefreshing(false);
                    authService.clearTokens();
                    return throwError(() => err);
                })
            );
        } else {
            authService.setIsRefreshing(false);
            authService.clearTokens();
            return throwError(() => new Error('No refresh token available'));
        }
    } else {
        return authService.getRefreshTokenSubject().pipe(
            filter(token => token !== null),
            take(1),
            switchMap(token => {
                return next(addTokenToRequest(request, token!));
            })
        );
    }
}

function addTokenToRequest(request: HttpRequest<unknown>, token: string): HttpRequest<unknown> {
    return request.clone({
        setHeaders: {
            Authorization: `Bearer ${token}`
        }
    });
}
