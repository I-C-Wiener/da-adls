import { HttpClient, HttpEvent, HttpEventType, HttpRequest } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { environment } from '@env/environment';

@Injectable({ providedIn: 'root' })
export class AttachmentService {
  constructor(private http: HttpClient) {}

  upload(conversationId: number, file: File, content?: string) {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('conversationId', `${conversationId}`);
    if (content) formData.append('content', content);

    const req = new HttpRequest('POST', `${environment.apiUrl}/attachments`, formData, {
      reportProgress: true,
    });

    return this.http.request(req);
  }

  downloadUrl(attachmentId: number): string {
    return `${environment.apiUrl}/attachments/${attachmentId}`;
  }
}
