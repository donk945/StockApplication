#import "KRSseModule.h"
#import <OpenKuiklyIOSRender/NSObject+KR.h>

@interface KRSseModule () <NSURLSessionDataDelegate>
@property (nonatomic, assign) NSInteger generation;
@property (nonatomic, strong) NSURLSession *session;
@property (nonatomic, strong) NSURLSessionDataTask *task;
@property (nonatomic, copy) KuiklyRenderCallback callback;
@property (nonatomic, strong) NSMutableData *byteBuffer;
@property (nonatomic, assign) NSInteger expectedStatus;
@end

@implementation KRSseModule

@synthesize hr_rootView;

- (void)start:(NSDictionary *)args {
    [self cancel:nil];
    NSInteger gen = ++self.generation;
    id raw = args[KR_PARAM_KEY];
    NSDictionary *root = nil;
    if ([raw isKindOfClass:[NSDictionary class]]) {
        root = raw;
    } else if ([raw isKindOfClass:[NSString class]]) {
        root = [raw hr_stringToDictionary];
    }
    KuiklyRenderCallback callback = args[KR_CALLBACK_KEY];
    if (![root isKindOfClass:[NSDictionary class]]) {
        [self emit:callback event:@"error" text:@"url 为空" generation:gen];
        return;
    }
    NSString *urlString = root[@"url"];
    if (urlString.length == 0) {
        [self emit:callback event:@"error" text:@"url 为空" generation:gen];
        return;
    }
    NSURL *url = [NSURL URLWithString:urlString];
    if (!url) {
        [self emit:callback event:@"error" text:@"url 无效" generation:gen];
        return;
    }
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    request.HTTPMethod = @"POST";
    request.timeoutInterval = 120;
    [request setValue:@"text/event-stream" forHTTPHeaderField:@"Accept"];
    [request setValue:@"application/json" forHTTPHeaderField:@"Content-Type"];
    id headers = root[@"headers"];
    if ([headers isKindOfClass:[NSDictionary class]]) {
        [(NSDictionary *)headers enumerateKeysAndObjectsUsingBlock:^(id key, id obj, BOOL *stop) {
            if ([key isKindOfClass:[NSString class]] && obj != nil) {
                [request setValue:[NSString stringWithFormat:@"%@", obj] forHTTPHeaderField:key];
            }
        }];
    }
    NSData *bodyData = [self jsonBodyFrom:root[@"body"]];
    request.HTTPBody = bodyData ?: [@"{}" dataUsingEncoding:NSUTF8StringEncoding];
    [request setValue:[NSString stringWithFormat:@"%lu", (unsigned long)request.HTTPBody.length]
  forHTTPHeaderField:@"Content-Length"];

    self.callback = callback;
    self.byteBuffer = [NSMutableData data];
    self.expectedStatus = 0;
    NSURLSessionConfiguration *config = [NSURLSessionConfiguration defaultSessionConfiguration];
    config.timeoutIntervalForRequest = 120;
    config.timeoutIntervalForResource = 180;
    self.session = [NSURLSession sessionWithConfiguration:config delegate:self delegateQueue:[NSOperationQueue mainQueue]];
    self.task = [self.session dataTaskWithRequest:request];
    [self.task resume];
}

- (void)cancel:(NSDictionary *)args {
    self.generation += 1;
    [self.task cancel];
    self.task = nil;
    [self.session invalidateAndCancel];
    self.session = nil;
    self.callback = nil;
    self.byteBuffer = nil;
}

- (NSData *)jsonBodyFrom:(id)body {
    if ([body isKindOfClass:[NSData class]]) {
        return body;
    }
    if ([body isKindOfClass:[NSString class]]) {
        return [(NSString *)body dataUsingEncoding:NSUTF8StringEncoding];
    }
    if ([body isKindOfClass:[NSDictionary class]] || [body isKindOfClass:[NSArray class]]) {
        return [NSJSONSerialization dataWithJSONObject:body options:0 error:nil];
    }
    return nil;
}

- (void)emit:(KuiklyRenderCallback)callback
       event:(NSString *)event
        text:(NSString *)text
  generation:(NSInteger)generation {
    if (generation != self.generation || callback == nil) {
        return;
    }
    dispatch_async(dispatch_get_main_queue(), ^{
        if (generation != self.generation) {
            return;
        }
        callback(@{
            @"event": event ?: @"",
            @"text": text ?: @""
        });
    });
}

- (void)handleLine:(NSString *)line generation:(NSInteger)generation {
    if (![line hasPrefix:@"data:"]) {
        return;
    }
    NSString *data = [[line substringFromIndex:5] stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
    if ([data isEqualToString:@"[DONE]"]) {
        [self emit:self.callback event:@"done" text:@"" generation:generation];
        [self.task cancel];
        return;
    }
    NSString *delta = [self extractDelta:data];
    if (delta.length > 0) {
        [self emit:self.callback event:@"delta" text:delta generation:generation];
    }
}

- (NSString *)extractDelta:(NSString *)data {
    NSData *bytes = [data dataUsingEncoding:NSUTF8StringEncoding];
    if (!bytes) {
        return @"";
    }
    id json = [NSJSONSerialization JSONObjectWithData:bytes options:0 error:nil];
    if (![json isKindOfClass:[NSDictionary class]]) {
        return @"";
    }
    id choices = json[@"choices"];
    if (![choices isKindOfClass:[NSArray class]] || [choices count] == 0) {
        return @"";
    }
    id first = choices[0];
    if (![first isKindOfClass:[NSDictionary class]]) {
        return @"";
    }
    id delta = first[@"delta"];
    if (![delta isKindOfClass:[NSDictionary class]]) {
        return @"";
    }
    id content = delta[@"content"];
    return [content isKindOfClass:[NSString class]] ? content : @"";
}

#pragma mark - NSURLSessionDataDelegate

- (void)URLSession:(NSURLSession *)session
          dataTask:(NSURLSessionDataTask *)dataTask
didReceiveResponse:(NSURLResponse *)response
 completionHandler:(void (^)(NSURLSessionResponseDisposition))completionHandler {
    NSInteger gen = self.generation;
    NSHTTPURLResponse *http = (NSHTTPURLResponse *)response;
    self.expectedStatus = http.statusCode;
    if (http.statusCode < 200 || http.statusCode > 299) {
        [self emit:self.callback event:@"error" text:[NSString stringWithFormat:@"请求失败(%ld)", (long)http.statusCode] generation:gen];
        completionHandler(NSURLSessionResponseCancel);
        return;
    }
    completionHandler(NSURLSessionResponseAllow);
}

- (void)URLSession:(NSURLSession *)session dataTask:(NSURLSessionDataTask *)dataTask didReceiveData:(NSData *)data {
    NSInteger gen = self.generation;
    if (gen != self.generation) {
        return;
    }
    [self.byteBuffer appendData:data];
    const uint8_t *bytes = self.byteBuffer.bytes;
    NSUInteger length = self.byteBuffer.length;
    NSUInteger start = 0;
    for (NSUInteger i = 0; i < length; i++) {
        if (bytes[i] != '\n') {
            continue;
        }
        NSData *lineData = [self.byteBuffer subdataWithRange:NSMakeRange(start, i - start)];
        NSString *line = [[NSString alloc] initWithData:lineData encoding:NSUTF8StringEncoding] ?: @"";
        if ([line hasSuffix:@"\r"]) {
            line = [line substringToIndex:line.length - 1];
        }
        [self handleLine:line generation:gen];
        start = i + 1;
    }
    if (start > 0) {
        [self.byteBuffer replaceBytesInRange:NSMakeRange(0, start) withBytes:NULL length:0];
    }
}

- (void)URLSession:(NSURLSession *)session task:(NSURLSessionTask *)task didCompleteWithError:(NSError *)error {
    NSInteger gen = self.generation;
    if (gen != self.generation) {
        return;
    }
    if (error && error.code != NSURLErrorCancelled) {
        [self emit:self.callback event:@"error" text:@"流式请求失败" generation:gen];
        return;
    }
    if (!error) {
        [self emit:self.callback event:@"done" text:@"" generation:gen];
    }
}

- (void)dealloc {
    [self cancel:nil];
}

@end
