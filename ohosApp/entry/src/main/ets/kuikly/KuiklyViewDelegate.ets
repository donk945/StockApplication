import {
  IKuiklyViewDelegate,
  KRRenderModuleExportCreator,
  KRRenderViewExportCreator
} from '@kuikly-open/render';
import { KRBridgeModule } from './modules/KRBridgeModule';
import { KRSseModule } from './modules/KRSseModule';

export class KuiklyViewDelegate extends IKuiklyViewDelegate {
  getCustomRenderViewCreatorRegisterMap(): Map<string, KRRenderViewExportCreator> {
    return new Map();
  }

  getCustomRenderModuleCreatorRegisterMap(): Map<string, KRRenderModuleExportCreator> {
    const map: Map<string, KRRenderModuleExportCreator> = new Map();
    map.set(KRBridgeModule.MODULE_NAME, () => new KRBridgeModule());
    map.set(KRSseModule.MODULE_NAME, () => new KRSseModule());
    return map;
  }
}