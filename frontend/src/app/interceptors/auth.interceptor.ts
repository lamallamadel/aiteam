import { HttpInterceptorFn, HttpRequest, HttpHandlerFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';
import { catchError, switchMap, filter, take } from 'rxjs/operators';
import { throwError, Observable } from 'rxjs';
import { Router } from '@angular/router';

export const authInterceptor: HttpInterceptorFn = (req: HttpRequest<unknown>, next: HttpHandlerFn) => {
    const authService = inject(AuthService);
    const router = inject(Router);
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
                return handle401Error(clonedReq, next, authService, router);
            }
            return throwError(() => error);
        })
    );
};

function handle401Error(
    request: HttpRequest<unknown>,
    next: HttpHandlerFn,
    authService: AuthService,
    router: Router
): Observable<any> {

    if (!authService.refreshing$.value) {
        authService.refreshing$.next(true);

        return authService.refreshAccessToken().pipe(
            switchMap((newAccessToken: string) => {
                authService.refreshing$.next(false);
                return next(addTokenToRequest(request, newAccessToken));
            }),
            catchError((err) => {
                authService.refreshing$.next(false);
                router.navigate(['/onboarding']);
                return throwError(() => err);
            })
        );
    } else {
        return authService.refreshing$.pipe(
            filter(refreshing => !refreshing),
            take(1),
            switchMap(() => {
                const newToken = authService.getAccessToken();
                if (newToken) {
                    return next(addTokenToRequest(request, newToken));
                } else {
                    router.navigate(['/onboarding']);
                    return throwError(() => new Error('No access token available after refresh'));
                }
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
