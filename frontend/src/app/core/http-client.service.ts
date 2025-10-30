import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpHeaders, HttpParams } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

export interface ApiResponse<T> {
  data: T;
  status: number;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class HttpClientService {
  private http = inject(HttpClient);

  get<T>(url: string, params?: HttpParams | {[param: string]: string | number | boolean}): Observable<ApiResponse<T>> {
    return this.http.get<T>(url, { params, observe: 'response' }).pipe(
      map(resp => ({ data: resp.body as T, status: resp.status, message: resp.statusText })),
      catchError(this.handleError)
    );
  }

  post<T>(url: string, body: any, options?: { headers?: HttpHeaders | {[header: string]: string | string[]} }): Observable<ApiResponse<T>> {
    return this.http.post<T>(url, body, { ...options, observe: 'response' }).pipe(
      map(resp => ({ data: resp.body as T, status: resp.status, message: resp.statusText })),
      catchError(this.handleError)
    );
  }

  put<T>(url: string, body: any, options?: { headers?: HttpHeaders | {[header: string]: string | string[]} }): Observable<ApiResponse<T>> {
    return this.http.put<T>(url, body, { ...options, observe: 'response' }).pipe(
      map(resp => ({ data: resp.body as T, status: resp.status, message: resp.statusText })),
      catchError(this.handleError)
    );
  }

  delete<T>(url: string, options?: { headers?: HttpHeaders | {[header: string]: string | string[]} }): Observable<ApiResponse<T>> {
    return this.http.delete<T>(url, { ...options, observe: 'response' }).pipe(
      map(resp => ({ data: resp.body as T, status: resp.status, message: resp.statusText })),
      catchError(this.handleError)
    );
  }

  private handleError(error: HttpErrorResponse) {
    let message = 'Unknown error';
    if (error.error instanceof ErrorEvent) {
      message = `Client error: ${error.error.message}`;
    } else {
      message = `Server error: ${error.status} - ${error.message}`;
    }
    // TODO: Add logging, user notification, and auth error handling here
    return throwError(() => ({ status: error.status, message, error }));
  }
}

