import { KRAny, KuiklyRenderBaseModule, KuiklyRenderCallback } from '@kuikly-open/render';
import http from '@ohos.net.http';
import util from '@ohos.util';

export class KRSseModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = 'KRSseModule';

  private generation: number = 0;
  private request: http.HttpRequest | null = null;
  private leftover: string = '';
  private decoder: util.TextDecoder = new util.TextDecoder('utf-8');

  syncMode(): boolean {
    return false;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    switch (method) {
      case 'start':
        this.start(params, callback);
        return null;
      case 'cancel':
        this.cancel();
        return null;
      default:
        return null;
    }
  }

  private start(params: KRAny, callback: KuiklyRenderCallback | null): void {
    this.cancel();
    const gen = ++this.generation;
    this.leftover = '';
    const root = this.parseRecord(params);
    const url = String(root['url'] ?? '');
    if (!url) {
      this.emit(callback, 'error', 'url 为空', gen);
      return;
    }
    const headers = this.asRecord(root['headers']);
    const body = this.bodyString(root['body']);
    const header: Record<string, string> = {
      'Accept': 'text/event-stream',
      'Content-Type': 'application/json',
    };
    Object.keys(headers).forEach((key) => {
      header[key] = String(headers[key] ?? '');
    });
    const req = http.createHttp();
    this.request = req;
    req.on('dataReceive', (data: ArrayBuffer) => {
      if (gen !== this.generation) {
        return;
      }
      const chunk = this.decoder.decodeWithStream(new Uint8Array(data));
      this.consume(this.leftover + chunk, callback, gen);
    });
    req.on('dataEnd', () => {
      if (gen !== this.generation) {
        return;
      }
      this.emit(callback, 'done', '', gen);
    });
    req.requestInStream(url, {
      method: http.RequestMethod.POST,
      header: header,
      extraData: body,
      connectTimeout: 15000,
      readTimeout: 120000,
    }).then((code: number) => {
      if (gen !== this.generation) {
        return;
      }
      if (code < 200 || code > 299) {
        this.emit(callback, 'error', `请求失败(${code})`, gen);
      }
    }).catch(() => {
      if (gen !== this.generation) {
        return;
      }
      this.emit(callback, 'error', '流式请求失败', gen);
    });
  }

  private cancel(): void {
    this.generation += 1;
    if (this.request) {
      try {
        this.request.off('dataReceive');
        this.request.off('dataEnd');
        this.request.destroy();
      } catch (_) {
      }
      this.request = null;
    }
    this.leftover = '';
  }

  private consume(text: string, callback: KuiklyRenderCallback | null, gen: number): void {
    const lines = text.split('\n');
    this.leftover = lines.pop() ?? '';
    for (let i = 0; i < lines.length; i++) {
      let line = lines[i];
      if (line.endsWith('\r')) {
        line = line.substring(0, line.length - 1);
      }
      if (!line.startsWith('data:')) {
        continue;
      }
      const data = line.substring(5).trim();
      if (data === '[DONE]') {
        this.emit(callback, 'done', '', gen);
        this.cancel();
        return;
      }
      const delta = this.extractDelta(data);
      if (delta.length > 0) {
        this.emit(callback, 'delta', delta, gen);
      }
    }
  }

  private extractDelta(data: string): string {
    try {
      const json = JSON.parse(data) as Record<string, Object>;
      const choices = json['choices'] as Object[] | undefined;
      if (!choices || choices.length === 0) {
        return '';
      }
      const first = choices[0] as Record<string, Object>;
      const delta = first['delta'] as Record<string, string> | undefined;
      return delta?.['content'] ?? '';
    } catch (_) {
      return '';
    }
  }

  private emit(
    callback: KuiklyRenderCallback | null,
    event: string,
    text: string,
    gen: number,
  ): void {
    if (gen !== this.generation || !callback) {
      return;
    }
    callback({ event: event, text: text });
  }

  private parseRecord(params: KRAny): Record<string, Object> {
    if (typeof params === 'string') {
      try {
        return JSON.parse(params) as Record<string, Object>;
      } catch (_) {
        return {};
      }
    }
    if (params && typeof params === 'object') {
      return params as Record<string, Object>;
    }
    return {};
  }

  private asRecord(value: Object | undefined): Record<string, Object> {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      return value as Record<string, Object>;
    }
    if (typeof value === 'string') {
      try {
        return JSON.parse(value) as Record<string, Object>;
      } catch (_) {
        return {};
      }
    }
    return {};
  }

  private bodyString(value: Object | undefined): string {
    if (typeof value === 'string') {
      return value.length > 0 ? value : '{}';
    }
    if (value && typeof value === 'object') {
      return JSON.stringify(value);
    }
    return '{}';
  }
}
