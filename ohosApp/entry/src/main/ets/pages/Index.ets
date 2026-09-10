import { ErrorReason, KRRecord, Kuikly, KuiklyRenderBaseView } from '@kuikly-open/render';
import { common, ConfigurationConstant } from '@kit.AbilityKit';
import router from '@ohos.router';
import { KuiklyViewDelegate } from '../kuikly/KuiklyViewDelegate';
import globalNativeManager from '../kuikly/MyNativeManager';
import { DEEPSEEK_API_KEY } from '../kuikly/generated/BuiltinApiKey';

@Entry
@Component
struct Index {
  kuiklyViewDelegate = new KuiklyViewDelegate();
  pageName?: string;
  pageData?: KRRecord;
  @State exception?: string = "";

  aboutToAppear() {
    AppStorage.setOrCreate<UIContext>("context", this.getUIContext());

    const params = router.getParams() as Record<string, Object>;
    this.pageName = (params?.pageName as string) ?? 'stock_chat';
    this.pageData = (params?.pageData as KRRecord) ?? this.defaultPageData();
  }

  build() {
    Stack() {
      Kuikly({
        pagerName: this.pageName ?? 'stock_chat',
        pagerData: this.pageData ?? {},
        delegate: this.kuiklyViewDelegate,
        nativeManager: globalNativeManager,
        onRenderException: (exception: Error, reason: ErrorReason) => {
          this.exception = `${exception.name}:\n${exception.message}`;
        },
      })
      if (this.exception) {
        Text(this.exception)
      }
    }
    .width('100%')
    .height('100%')
    .expandSafeArea([SafeAreaType.KEYBOARD])
  }

  onPageShow(): void {
    this.kuiklyViewDelegate.pageDidAppear();
  }

  onPageHide(): void {
    this.kuiklyViewDelegate.pageDidDisappear();
  }

  private defaultPageData(): KRRecord {
    let night = 0;
    try {
      const ctx = getContext(this) as common.UIAbilityContext;
      night = ctx.config.colorMode === ConfigurationConstant.ColorMode.COLOR_MODE_DARK ? 1 : 0;
    } catch (_) {
    }
    return {
      appId: 1,
      isNightMode: night,
      deepseekApiKey: DEEPSEEK_API_KEY,
    };
  }
}
