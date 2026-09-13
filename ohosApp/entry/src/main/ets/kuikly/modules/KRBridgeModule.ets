import { common } from '@kit.AbilityKit';
import { BusinessError } from '@kit.BasicServicesKit';
import { pasteboard } from '@kit.BasicServicesKit';
import { KRAny } from '@kuikly-open/render';
import { KuiklyRenderBaseModule } from '@kuikly-open/render';
import { KuiklyRenderCallback } from '@kuikly-open/render';
import { promptAction } from '@kit.ArkUI';
import { inputMethod } from '@kit.IMEKit';
import router from '@ohos.router';
import util from '@ohos.util';
import fs from '@ohos.file.fs';
import { fileUri } from '@kit.CoreFileKit';

export class KRBridgeModule extends KuiklyRenderBaseModule {
  static readonly MODULE_NAME = "HRBridgeModule";
  static readonly SHOW_ALERT = "showAlert";
  static readonly CLOSE_PAGE = "closePage";
  static readonly OPEN_PAGE = "openPage";
  static readonly COPY_TO_PASTE_BOARD = "copyToPasteboard";
  static readonly TOAST = "toast";
  static readonly LOG = "log";
  static readonly REPORT_DT = "reportDT";
  static readonly REPORT_REAL_TIME = "reportRealtime";
  static readonly LOCAL_SERVE_TIME = "localServeTime";
  static readonly CURRENT_TIME_STAMP = "currentTimestamp";
  static readonly DATE_FORMATTER = "dateFormatter";
  static readonly READ_ASSET_FILE = "readAssetFile";
  static readonly CLOSE_KEYBOARD = "closeKeyboard";
  static readonly SET_NIGHT_BARS = "setNightBars";

  syncMode(): boolean {
    return true;
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback): KRAny {
    switch (method) {
      case KRBridgeModule.SHOW_ALERT: {
        this.showAlert(params, callback);
        break;
      }
      case KRBridgeModule.CLOSE_PAGE: {
        this.closePage();
        break;
      }
      case KRBridgeModule.OPEN_PAGE: {
        this.openPage(params);
        break;
      }
      case KRBridgeModule.COPY_TO_PASTE_BOARD: {
        this.copyToPasteboard(params);
        break;
      }
      case KRBridgeModule.TOAST: {
        this.toast(params);
        break;
      }
      case KRBridgeModule.LOG: {
        this.log(params);
        break;
      }
      case KRBridgeModule.REPORT_DT: {
        this.reportDT(params);
        break;
      }
      case KRBridgeModule.REPORT_REAL_TIME: {
        this.reportRealtime(params);
        break;
      }
      case KRBridgeModule.LOCAL_SERVE_TIME: {
        this.localServeTime(params, callback);
        break;
      }
      case KRBridgeModule.CURRENT_TIME_STAMP: {
        return this.currentTimestamp();
      }
      case KRBridgeModule.DATE_FORMATTER: {
        return this.dateFormatter(params);
      }
      case KRBridgeModule.READ_ASSET_FILE: {
        this.readAssetFile(params, callback);
        break;
      }
      case KRBridgeModule.CLOSE_KEYBOARD: {
        this.closeKeyboard();
        return '';
      }
      case KRBridgeModule.SET_NIGHT_BARS: {
        this.setNightBars(params);
        break;
      }
      case 'debugMark':
      case 'loadDebugMark': {
        return '';
      }
      default:
        break;
    }
    return null;
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

  private showAlert(params: KRAny, callback: KuiklyRenderCallback) {
  }

  private closePage() {
    router.back();
  }

  private openPage(params: KRAny) {
    const rec = this.parseRecord(params);
    const url = String(rec['url'] ?? rec['pageName'] ?? '');
    if (!url) {
      return;
    }
    router.pushUrl({
      url: 'pages/Index',
      params: {
        pageName: url,
        pageData: rec['userData'] ?? rec['pageData'] ?? {},
      },
    });
  }

  private copyToPasteboard(params: KRAny) {
    const content = String(this.parseRecord(params)['content'] ?? '');
    if (!content) {
      return;
    }
    const data = pasteboard.createData(pasteboard.MIMETYPE_TEXT_PLAIN, content);
    pasteboard.getSystemPasteboard().setData(data);
  }

  private toast(params: KRAny) {
    const content = String(this.parseRecord(params)['content'] ?? '');
    if (!content) {
      return;
    }
    const ui = AppStorage.get('context') as UIContext | undefined;
    if (ui) {
      ui.getPromptAction().showToast({ message: content, duration: 2000 });
      return;
    }
    promptAction.showToast({ message: content, duration: 2000 });
  }

  private log(params: KRAny) {
    if (params == null) {
      return;
    }
    const rec = this.parseRecord(params);
    const content = rec['content'] ?? params;
    console.log(`KuiklyRender ${JSON.stringify(content)}`);
  }

  private reportDT(params: KRAny) {
  }

  private reportRealtime(params: KRAny) {
  }

  private localServeTime(params: KRAny, callback: KuiklyRenderCallback) {
    callback({ "time": Date.now() / 1000.0 });
  }

  private currentTimestamp(): string {
    return Date.now().toString();
  }

  private dateFormatter(params: KRAny): string {
    const rec = this.parseRecord(params);
    const stamp = Number(rec['timeStamp'] ?? 0);
    const format = String(rec['format'] ?? 'yyyy-MM-dd HH:mm:ss');
    const date = new Date(stamp);
    const pad = (num: number): string => {
      return num < 10 ? `0${num}` : `${num}`;
    };
    return format
      .replace('yyyy', `${date.getFullYear()}`)
      .replace('MM', pad(date.getMonth() + 1))
      .replace('dd', pad(date.getDate()))
      .replace('HH', pad(date.getHours()))
      .replace('mm', pad(date.getMinutes()))
      .replace('ss', pad(date.getSeconds()));
  }

  private closeKeyboard() {
    try {
      inputMethod.getController().hideTextInput();
    } catch (_) {
    }
  }

  private setNightBars(params: KRAny) {
    const night = Number(this.parseRecord(params)['night'] ?? 0) === 1;
    const bg = night ? '#FF111113' : '#FFFFFFFF';
    const content = night ? '#FFFFFFFF' : '#FF000000';
    try {
      const context = getContext(this) as common.UIAbilityContext;
      const mainWindow = context.windowStage.getMainWindowSync();
      mainWindow.setWindowSystemBarProperties({
        statusBarColor: bg,
        navigationBarColor: bg,
        statusBarContentColor: content,
        navigationBarContentColor: content,
      });
    } catch (e) {
      console.error(`setNightBars failed ${JSON.stringify(e)}`);
    }
  }

  private readAssetFile(params: KRAny, callback: KuiklyRenderCallback) {
    let records = this.parseRecord(params) as Record<string, string>;
    let assetPath = records["assetPath"];
    let context = getContext(this) as common.UIAbilityContext;
    let resDir = context.resourceDir;
    let path = resDir + '/' + assetPath;
    let assetURI = fileUri.getUriFromPath(path);
    const file = fs.openSync(assetURI, fs.OpenMode.READ_ONLY);
    const stat = fs.statSync(file.fd);
    const arrayBuffer = new ArrayBuffer(stat.size);
    fs.read(file.fd, arrayBuffer, (err: BusinessError, readLen: number) => {
      if (err) {
        console.error("read failed with error message: " + err.message + ", error code: " + err.code);
      } else {
        let buf = new Uint8Array(arrayBuffer, 0, readLen);
        const textDecoder = new util.TextDecoder('utf-8');
        const res = textDecoder.decodeWithStream(new Uint8Array(buf));
        callback({
          'result': JSON.parse(res)
        });
      }
      fs.closeSync(file);
    });
  }
}
