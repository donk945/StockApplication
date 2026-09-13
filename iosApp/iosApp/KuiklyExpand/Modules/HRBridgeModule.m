#import "HRBridgeModule.h"
#import <UIKit/UIKit.h>
#import <OpenKuiklyIOSRender/NSObject+KR.h>

@implementation HRBridgeModule

@synthesize hr_rootView;

- (NSDictionary *)paramsFrom:(NSDictionary *)args {
    id raw = args[KR_PARAM_KEY];
    if ([raw isKindOfClass:[NSDictionary class]]) {
        return raw;
    }
    if ([raw isKindOfClass:[NSString class]]) {
        return [raw hr_stringToDictionary] ?: @{};
    }
    return @{};
}

- (UIViewController *)hostViewController {
    UIResponder *responder = self.hr_rootView;
    while (responder) {
        if ([responder isKindOfClass:[UIViewController class]]) {
            return (UIViewController *)responder;
        }
        responder = responder.nextResponder;
    }
    return nil;
}

- (void)copyToPasteboard:(NSDictionary *)args {
    NSString *content = [self paramsFrom:args][@"content"];
    if (content.length == 0) {
        return;
    }
    UIPasteboard.generalPasteboard.string = content;
}

- (void)log:(NSDictionary *)args {
    NSString *content = [self paramsFrom:args][@"content"];
    NSLog(@"KuiklyRender:%@", content ?: @"");
}

- (void)toast:(NSDictionary *)args {
    NSString *content = [self paramsFrom:args][@"content"];
    if (content.length == 0) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        UIView *host = self.hr_rootView.window ?: self.hr_rootView;
        if (!host) {
            return;
        }
        UILabel *label = [[UILabel alloc] init];
        label.text = content;
        label.textColor = UIColor.whiteColor;
        label.font = [UIFont systemFontOfSize:13];
        label.numberOfLines = 0;
        label.textAlignment = NSTextAlignmentCenter;
        label.backgroundColor = [[UIColor blackColor] colorWithAlphaComponent:0.78];
        label.layer.cornerRadius = 8;
        label.clipsToBounds = YES;
        CGFloat maxWidth = host.bounds.size.width - 48;
        CGSize size = [label sizeThatFits:CGSizeMake(maxWidth, CGFLOAT_MAX)];
        CGFloat width = MIN(maxWidth, MAX(120, size.width + 24));
        CGFloat height = size.height + 16;
        label.frame = CGRectMake((host.bounds.size.width - width) / 2.0, 88, width, height);
        [host addSubview:label];
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(2.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            [label removeFromSuperview];
        });
    });
}

- (id)closeKeyboard:(NSDictionary *)args {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.hr_rootView endEditing:YES];
        UIWindow *window = self.hr_rootView.window;
        [window endEditing:YES];
    });
    return @"";
}

- (void)setNightBars:(NSDictionary *)args {
    BOOL night = [[self paramsFrom:args][@"night"] intValue] == 1;
    dispatch_async(dispatch_get_main_queue(), ^{
        UIColor *bg = night
            ? [UIColor colorWithRed:17 / 255.0 green:17 / 255.0 blue:19 / 255.0 alpha:1]
            : UIColor.whiteColor;
        self.hr_rootView.backgroundColor = bg;
        UIViewController *host = [self hostViewController];
        host.view.backgroundColor = bg;
        host.navigationController.view.backgroundColor = bg;
        if (@available(iOS 13.0, *)) {
            UIUserInterfaceStyle style = night ? UIUserInterfaceStyleDark : UIUserInterfaceStyleLight;
            host.overrideUserInterfaceStyle = style;
            host.navigationController.overrideUserInterfaceStyle = style;
        }
    });
}

- (void)closePage:(NSDictionary *)args {
    dispatch_async(dispatch_get_main_queue(), ^{
        UIViewController *vc = [self hostViewController];
        if (vc.navigationController.viewControllers.count > 1) {
            [vc.navigationController popViewControllerAnimated:YES];
        } else {
            [vc dismissViewControllerAnimated:YES completion:nil];
        }
    });
}

- (id)currentTimestamp:(NSDictionary *)args {
    long long ms = (long long)([[NSDate date] timeIntervalSince1970] * 1000.0);
    return [NSString stringWithFormat:@"%lld", ms];
}

- (id)dateFormatter:(NSDictionary *)args {
    NSDictionary *params = [self paramsFrom:args];
    long long stamp = [params[@"timeStamp"] longLongValue];
    NSString *format = params[@"format"] ?: @"yyyy-MM-dd HH:mm:ss";
    NSDateFormatter *formatter = [[NSDateFormatter alloc] init];
    formatter.locale = [NSLocale localeWithLocaleIdentifier:@"en_US_POSIX"];
    formatter.dateFormat = format;
    return [formatter stringFromDate:[NSDate dateWithTimeIntervalSince1970:stamp / 1000.0]] ?: @"";
}

- (void)localServeTime:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    if (callback) {
        callback(@{ @"time": @([[NSDate date] timeIntervalSince1970]) });
    }
}

- (id)debugMark:(NSDictionary *)args {
    return @"";
}

- (id)loadDebugMark:(NSDictionary *)args {
    return @"";
}

@end
