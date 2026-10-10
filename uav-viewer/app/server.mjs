import { createRequire } from 'node:module'; const require = createRequire(import.meta.url);

// node_modules/hono/dist/compose.js
var compose = (middleware, onError, onNotFound) => {
  return (context, next) => {
    let index = -1;
    return dispatch(0);
    async function dispatch(i) {
      if (i <= index) {
        throw new Error("next() called multiple times");
      }
      index = i;
      let res;
      let isError = false;
      let handler;
      if (middleware[i]) {
        handler = middleware[i][0][0];
        context.req.routeIndex = i;
      } else {
        handler = i === middleware.length && next || void 0;
      }
      if (handler) {
        try {
          res = await handler(context, () => dispatch(i + 1));
        } catch (err) {
          if (err instanceof Error && onError) {
            context.error = err;
            res = await onError(err, context);
            isError = true;
          } else {
            throw err;
          }
        }
      } else {
        if (context.finalized === false && onNotFound) {
          res = await onNotFound(context);
        }
      }
      if (res && (context.finalized === false || isError)) {
        context.res = res;
      }
      return context;
    }
  };
};

// node_modules/hono/dist/request/constants.js
var GET_MATCH_RESULT = /* @__PURE__ */ Symbol();

// node_modules/hono/dist/utils/buffer.js
var bufferToFormData = (arrayBuffer, contentType2) => {
  const response = new Response(arrayBuffer, {
    headers: {
      // Normalize the media type (case-insensitive) while keeping parameters like the boundary
      "Content-Type": contentType2.replace(/^[^;]+/, (mediaType) => mediaType.toLowerCase())
    }
  });
  return response.formData();
};

// node_modules/hono/dist/utils/body.js
var MAX_NESTING_DEPTH = 32;
var MAX_NESTED_OBJECTS = 1e4;
var isRawRequest = (request) => "headers" in request;
var parseBody = async (request, options = /* @__PURE__ */ Object.create(null)) => {
  const { all: all2 = false, dot = false } = options;
  const headers = isRawRequest(request) ? request.headers : request.raw.headers;
  const contentType2 = headers.get("Content-Type");
  const mediaType = contentType2?.split(";")[0].trim().toLowerCase();
  if (mediaType === "multipart/form-data" || mediaType === "application/x-www-form-urlencoded") {
    return parseFormData(request, { all: all2, dot });
  }
  return {};
};
async function parseFormData(request, options) {
  if (!isRawRequest(request) && request.bodyCache.formData) {
    return convertFormDataToBodyData(
      await request.bodyCache.formData,
      options
    );
  }
  const headers = isRawRequest(request) ? request.headers : request.raw.headers;
  const arrayBuffer = await request.arrayBuffer();
  const formDataPromise = bufferToFormData(arrayBuffer, headers.get("Content-Type") || "");
  if (!isRawRequest(request)) {
    request.bodyCache.formData = formDataPromise;
  }
  const formData = await formDataPromise;
  if (formData) {
    return convertFormDataToBodyData(formData, options);
  }
  return {};
}
function convertFormDataToBodyData(formData, options) {
  const form = /* @__PURE__ */ Object.create(null);
  const nestingState = { count: 0 };
  formData.forEach((value, key) => {
    const shouldParseAllValues = options.all || key.endsWith("[]");
    if (!shouldParseAllValues) {
      form[key] = value;
    } else {
      handleParsingAllValues(form, key, value);
    }
  });
  if (options.dot) {
    Object.entries(form).forEach(([key, value]) => {
      const shouldParseDotValues = key.includes(".");
      if (shouldParseDotValues) {
        handleParsingNestedValues(form, key, value, nestingState);
        delete form[key];
      }
    });
  }
  return form;
}
var handleParsingAllValues = (form, key, value) => {
  if (form[key] !== void 0) {
    if (Array.isArray(form[key])) {
      ;
      form[key].push(value);
    } else {
      form[key] = [form[key], value];
    }
  } else {
    if (!key.endsWith("[]")) {
      form[key] = value;
    } else {
      form[key] = [value];
    }
  }
};
var handleParsingNestedValues = (form, key, value, state) => {
  if (/(?:^|\.)__proto__\./.test(key)) {
    return;
  }
  let nestedForm = form;
  const keys = key.split(".", MAX_NESTING_DEPTH + 2);
  if (keys.length > MAX_NESTING_DEPTH + 1) {
    throwNestingLimitExceeded();
  }
  keys.forEach((key2, index) => {
    if (index === keys.length - 1) {
      nestedForm[key2] = value;
    } else {
      if (!nestedForm[key2] || typeof nestedForm[key2] !== "object" || Array.isArray(nestedForm[key2]) || nestedForm[key2] instanceof File) {
        if (state.count++ >= MAX_NESTED_OBJECTS) {
          throwNestingLimitExceeded();
        }
        nestedForm[key2] = /* @__PURE__ */ Object.create(null);
      }
      nestedForm = nestedForm[key2];
    }
  });
};
var throwNestingLimitExceeded = () => {
  throw new Error("Nesting limit exceeded");
};

// node_modules/hono/dist/utils/url.js
var splitPath = (path) => {
  const paths = path.split("/");
  if (paths[0] === "") {
    paths.shift();
  }
  return paths;
};
var splitRoutingPath = (routePath) => {
  const { groups, path } = extractGroupsFromPath(routePath);
  const paths = splitPath(path);
  return replaceGroupMarks(paths, groups);
};
var extractGroupsFromPath = (path) => {
  const groups = [];
  path = path.replace(/\{[^}]+\}/g, (match2, index) => {
    const mark = `@${index}`;
    groups.push([mark, match2]);
    return mark;
  });
  return { groups, path };
};
var replaceGroupMarks = (paths, groups) => {
  for (let i = groups.length - 1; i >= 0; i--) {
    const [mark] = groups[i];
    for (let j = paths.length - 1; j >= 0; j--) {
      if (paths[j].includes(mark)) {
        paths[j] = paths[j].replace(mark, groups[i][1]);
        break;
      }
    }
  }
  return paths;
};
var patternCache = {};
var getPattern = (label, next) => {
  if (label === "*") {
    return "*";
  }
  const match2 = label.match(/^\:([^\{\}]+)(?:\{(.+)\})?$/);
  if (match2) {
    const cacheKey2 = `${label}#${next}`;
    if (!patternCache[cacheKey2]) {
      if (match2[2]) {
        patternCache[cacheKey2] = next && next[0] !== ":" && next[0] !== "*" ? [cacheKey2, match2[1], new RegExp(`^${match2[2]}(?=/${next})`)] : [label, match2[1], new RegExp(`^${match2[2]}$`)];
      } else {
        patternCache[cacheKey2] = [label, match2[1], true];
      }
    }
    return patternCache[cacheKey2];
  }
  return null;
};
var tryDecode = (str2, decoder) => {
  try {
    return decoder(str2);
  } catch {
    return str2.replace(/(?:%[0-9A-Fa-f]{2})+/g, (match2) => {
      try {
        return decoder(match2);
      } catch {
        return match2;
      }
    });
  }
};
var tryDecodeURI = (str2) => tryDecode(str2, decodeURI);
var getPath = (request) => {
  const url2 = request.url;
  const start = url2.indexOf("/", url2.indexOf(":") + 4);
  let i = start;
  for (; i < url2.length; i++) {
    const charCode = url2.charCodeAt(i);
    if (charCode === 37) {
      const queryIndex = url2.indexOf("?", i);
      const hashIndex = url2.indexOf("#", i);
      const end = queryIndex === -1 ? hashIndex === -1 ? void 0 : hashIndex : hashIndex === -1 ? queryIndex : Math.min(queryIndex, hashIndex);
      const path = url2.slice(start, end);
      return tryDecodeURI(path.includes("%25") ? path.replace(/%25/g, "%2525") : path);
    } else if (charCode === 63 || charCode === 35) {
      break;
    }
  }
  return url2.slice(start, i);
};
var getPathNoStrict = (request) => {
  const result = getPath(request);
  return result.length > 1 && result.at(-1) === "/" ? result.slice(0, -1) : result;
};
var mergePath = (base, sub, ...rest) => {
  if (rest.length) {
    sub = mergePath(sub, ...rest);
  }
  return `${base?.[0] === "/" ? "" : "/"}${base}${sub === "/" ? "" : `${base?.at(-1) === "/" ? "" : "/"}${sub?.[0] === "/" ? sub.slice(1) : sub}`}`;
};
var checkOptionalParameter = (path) => {
  if (path.charCodeAt(path.length - 1) !== 63 || !path.includes(":")) {
    return null;
  }
  const segments = path.split("/");
  const results = [];
  let basePath = "";
  segments.forEach((segment) => {
    if (segment !== "" && !/\:/.test(segment)) {
      basePath += "/" + segment;
    } else if (/\:/.test(segment)) {
      if (segment.charCodeAt(segment.length - 1) === 63) {
        if (results.length === 0 && basePath === "") {
          results.push("/");
        } else {
          results.push(basePath);
        }
        const optionalSegment = segment.slice(0, -1);
        basePath += "/" + optionalSegment;
        results.push(basePath);
      } else {
        basePath += "/" + segment;
      }
    }
  });
  return results.filter((v, i, a) => a.indexOf(v) === i);
};
var tryDecodeURIComponent = (str2) => str2.indexOf("%") !== -1 ? tryDecode(str2, decodeURIComponent_) : str2;
var _decodeURI = (value) => {
  if (value.indexOf("+") !== -1) {
    value = value.replace(/\+/g, " ");
  }
  return tryDecodeURIComponent(value);
};
var _getQueryParam = (url2, key, multiple) => {
  const hashIndex = url2.indexOf("#", 8);
  if (hashIndex !== -1) {
    url2 = url2.slice(0, hashIndex);
  }
  let encoded;
  if (!multiple && key && key.indexOf("%") === -1 && key.indexOf("+") === -1) {
    let keyIndex2 = url2.indexOf("?", 8);
    if (keyIndex2 === -1) {
      return void 0;
    }
    if (!url2.startsWith(key, keyIndex2 + 1)) {
      keyIndex2 = url2.indexOf(`&${key}`, keyIndex2 + 1);
    }
    while (keyIndex2 !== -1) {
      const trailingKeyCode = url2.charCodeAt(keyIndex2 + key.length + 1);
      if (trailingKeyCode === 61) {
        const valueIndex = keyIndex2 + key.length + 2;
        const endIndex = url2.indexOf("&", valueIndex);
        return _decodeURI(url2.slice(valueIndex, endIndex === -1 ? void 0 : endIndex));
      } else if (trailingKeyCode == 38 || isNaN(trailingKeyCode)) {
        return "";
      }
      keyIndex2 = url2.indexOf(`&${key}`, keyIndex2 + 1);
    }
    encoded = /[%+]/.test(url2);
    if (!encoded) {
      return void 0;
    }
  }
  const results = /* @__PURE__ */ Object.create(null);
  encoded ??= /[%+]/.test(url2);
  let keyIndex = url2.indexOf("?", 8);
  while (keyIndex !== -1) {
    const nextKeyIndex = url2.indexOf("&", keyIndex + 1);
    let valueIndex = url2.indexOf("=", keyIndex);
    if (valueIndex > nextKeyIndex && nextKeyIndex !== -1) {
      valueIndex = -1;
    }
    let name = url2.slice(
      keyIndex + 1,
      valueIndex === -1 ? nextKeyIndex === -1 ? void 0 : nextKeyIndex : valueIndex
    );
    if (encoded) {
      name = _decodeURI(name);
    }
    keyIndex = nextKeyIndex;
    if (name === "") {
      continue;
    }
    let value;
    if (valueIndex === -1) {
      value = "";
    } else {
      value = url2.slice(valueIndex + 1, nextKeyIndex === -1 ? void 0 : nextKeyIndex);
      if (encoded) {
        value = _decodeURI(value);
      }
    }
    if (multiple) {
      if (!(results[name] && Array.isArray(results[name]))) {
        results[name] = [];
      }
      ;
      results[name].push(value);
    } else {
      results[name] ??= value;
    }
  }
  return key ? results[key] : results;
};
var getQueryParam = _getQueryParam;
var getQueryParams = (url2, key) => {
  return _getQueryParam(url2, key, true);
};
var decodeURIComponent_ = decodeURIComponent;

// node_modules/hono/dist/request.js
var HonoRequest = class {
  /**
   * `.raw` can get the raw Request object.
   *
   * @see {@link https://hono.dev/docs/api/request#raw}
   *
   * @example
   * ```ts
   * // For Cloudflare Workers
   * app.post('/', async (c) => {
   *   const metadata = c.req.raw.cf?.hostMetadata?
   *   ...
   * })
   * ```
   */
  raw;
  #validatedData;
  // Short name of validatedData
  #matchResult;
  routeIndex = 0;
  /**
   * `.path` can get the pathname of the request.
   *
   * @see {@link https://hono.dev/docs/api/request#path}
   *
   * @example
   * ```ts
   * app.get('/about/me', (c) => {
   *   const pathname = c.req.path // `/about/me`
   * })
   * ```
   */
  path;
  bodyCache = {};
  constructor(request, path = "/", matchResult = [[]]) {
    this.raw = request;
    this.path = path;
    this.#matchResult = matchResult;
  }
  param(key) {
    return key ? this.#getDecodedParam(key) : this.#getAllDecodedParams();
  }
  #getDecodedParam(key) {
    const paramKey = this.#matchResult[0][this.routeIndex]?.[1][key];
    const param = this.#getParamValue(paramKey);
    return param && tryDecodeURIComponent(param);
  }
  #getAllDecodedParams() {
    const decoded = {};
    const keys = Object.keys(this.#matchResult[0][this.routeIndex]?.[1] ?? {});
    for (const key of keys) {
      const value = this.#getParamValue(this.#matchResult[0][this.routeIndex][1][key]);
      if (value !== void 0) {
        decoded[key] = tryDecodeURIComponent(value);
      }
    }
    return decoded;
  }
  #getParamValue(paramKey) {
    return this.#matchResult[1] ? this.#matchResult[1][paramKey] : paramKey;
  }
  query(key) {
    return getQueryParam(this.url, key);
  }
  queries(key) {
    return getQueryParams(this.url, key);
  }
  header(name) {
    if (name) {
      return this.raw.headers.get(name) ?? void 0;
    }
    const headerData = /* @__PURE__ */ Object.create(null);
    this.raw.headers.forEach((value, key) => {
      headerData[key] = value;
    });
    return headerData;
  }
  async parseBody(options) {
    return parseBody(this, options);
  }
  #cachedBody = (key) => {
    const { bodyCache, raw: raw2 } = this;
    const cachedBody = bodyCache[key];
    if (cachedBody) {
      return cachedBody;
    }
    for (const anyCachedKey in bodyCache) {
      return bodyCache[anyCachedKey].then((body) => {
        if (anyCachedKey === "json") {
          body = JSON.stringify(body);
        }
        const contentType2 = anyCachedKey === "formData" ? void 0 : raw2.headers.get("content-type");
        return new Response(body, {
          headers: contentType2 ? { "Content-Type": contentType2 } : void 0
        })[key]();
      });
    }
    return bodyCache[key] = raw2[key]();
  };
  /**
   * `.json()` can parse Request body of type `application/json`
   *
   * @see {@link https://hono.dev/docs/api/request#json}
   *
   * @example
   * ```ts
   * app.post('/entry', async (c) => {
   *   const body = await c.req.json()
   * })
   * ```
   */
  json() {
    return this.#cachedBody("text").then((text) => JSON.parse(text));
  }
  /**
   * `.text()` can parse Request body of type `text/plain`
   *
   * @see {@link https://hono.dev/docs/api/request#text}
   *
   * @example
   * ```ts
   * app.post('/entry', async (c) => {
   *   const body = await c.req.text()
   * })
   * ```
   */
  text() {
    return this.#cachedBody("text");
  }
  /**
   * `.arrayBuffer()` parse Request body as an `ArrayBuffer`
   *
   * @see {@link https://hono.dev/docs/api/request#arraybuffer}
   *
   * @example
   * ```ts
   * app.post('/entry', async (c) => {
   *   const body = await c.req.arrayBuffer()
   * })
   * ```
   */
  arrayBuffer() {
    return this.#cachedBody("arrayBuffer");
  }
  /**
   * `.bytes()` parses the request body as a `Uint8Array`.
   *
   * @see {@link https://hono.dev/docs/api/request#bytes}
   *
   * @example
   * ```ts
   * app.post('/entry', async (c) => {
   *   const body = await c.req.bytes()
   * })
   * ```
   */
  bytes() {
    return this.#cachedBody("arrayBuffer").then((buffer) => new Uint8Array(buffer));
  }
  /**
   * Parses the request body as a `Blob`.
   * @example
   * ```ts
   * app.post('/entry', async (c) => {
   *   const body = await c.req.blob();
   * });
   * ```
   * @see https://hono.dev/docs/api/request#blob
   */
  blob() {
    return this.#cachedBody("blob");
  }
  /**
   * Parses the request body as `FormData`.
   * @example
   * ```ts
   * app.post('/entry', async (c) => {
   *   const body = await c.req.formData();
   * });
   * ```
   * @see https://hono.dev/docs/api/request#formdata
   */
  formData() {
    return this.#cachedBody("formData");
  }
  /**
   * Adds validated data to the request.
   *
   * @param target - The target of the validation.
   * @param data - The validated data to add.
   */
  addValidatedData(target, data) {
    ;
    (this.#validatedData ??= {})[target] = data;
  }
  valid(target) {
    return this.#validatedData?.[target];
  }
  /**
   * `.url()` can get the request url strings.
   *
   * @see {@link https://hono.dev/docs/api/request#url}
   *
   * @example
   * ```ts
   * app.get('/about/me', (c) => {
   *   const url = c.req.url // `http://localhost:8787/about/me`
   *   ...
   * })
   * ```
   */
  get url() {
    return this.raw.url;
  }
  /**
   * `.method()` can get the method name of the request.
   *
   * @see {@link https://hono.dev/docs/api/request#method}
   *
   * @example
   * ```ts
   * app.get('/about/me', (c) => {
   *   const method = c.req.method // `GET`
   * })
   * ```
   */
  get method() {
    return this.raw.method;
  }
  get [GET_MATCH_RESULT]() {
    return this.#matchResult;
  }
  /**
   * `.matchedRoutes()` can return a matched route in the handler
   *
   * @deprecated
   *
   * Use matchedRoutes helper defined in "hono/route" instead.
   *
   * @see {@link https://hono.dev/docs/api/request#matchedroutes}
   *
   * @example
   * ```ts
   * app.use('*', async function logger(c, next) {
   *   await next()
   *   c.req.matchedRoutes.forEach(({ handler, method, path }, i) => {
   *     const name = handler.name || (handler.length < 2 ? '[handler]' : '[middleware]')
   *     console.log(
   *       method,
   *       ' ',
   *       path,
   *       ' '.repeat(Math.max(10 - path.length, 0)),
   *       name,
   *       i === c.req.routeIndex ? '<- respond from here' : ''
   *     )
   *   })
   * })
   * ```
   */
  get matchedRoutes() {
    return this.#matchResult[0].map(([[, route]]) => route);
  }
  /**
   * `routePath()` can retrieve the path registered within the handler
   *
   * @deprecated
   *
   * Use routePath helper defined in "hono/route" instead.
   *
   * @see {@link https://hono.dev/docs/api/request#routepath}
   *
   * @example
   * ```ts
   * app.get('/posts/:id', (c) => {
   *   return c.json({ path: c.req.routePath })
   * })
   * ```
   */
  get routePath() {
    return this.#matchResult[0].map(([[, route]]) => route)[this.routeIndex].path;
  }
};

// node_modules/hono/dist/utils/html.js
var HtmlEscapedCallbackPhase = {
  Stringify: 1,
  BeforeStream: 2,
  Stream: 3
};
var raw = (value, callbacks) => {
  const escapedString = new String(value);
  escapedString.isEscaped = true;
  escapedString.callbacks = callbacks;
  return escapedString;
};
var resolveCallback = async (str2, phase, preserveCallbacks, context, buffer) => {
  if (typeof str2 === "object" && !(str2 instanceof String)) {
    if (!(str2 instanceof Promise)) {
      str2 = str2.toString();
    }
    if (str2 instanceof Promise) {
      str2 = await str2;
    }
  }
  const callbacks = str2.callbacks;
  if (!callbacks?.length) {
    return Promise.resolve(str2);
  }
  if (buffer) {
    buffer[0] += str2;
  } else {
    buffer = [str2];
  }
  const resStr = Promise.all(callbacks.map((c) => c({ phase, buffer, context }))).then(
    (res) => Promise.all(
      res.filter(Boolean).map((str22) => resolveCallback(str22, phase, false, context, buffer))
    ).then(() => buffer[0])
  );
  if (preserveCallbacks) {
    return raw(await resStr, callbacks);
  } else {
    return resStr;
  }
};

// node_modules/hono/dist/context.js
var TEXT_PLAIN = "text/plain; charset=UTF-8";
var setDefaultContentType = (contentType2, headers) => {
  return {
    "Content-Type": contentType2,
    ...headers
  };
};
var createResponseInstance = (body, init) => new Response(body, init);
var Context = class {
  #rawRequest;
  #req;
  /**
   * `.env` can get bindings (environment variables, secrets, KV namespaces, D1 database, R2 bucket etc.) in Cloudflare Workers.
   *
   * @see {@link https://hono.dev/docs/api/context#env}
   *
   * @example
   * ```ts
   * // Environment object for Cloudflare Workers
   * app.get('*', async c => {
   *   const counter = c.env.COUNTER
   * })
   * ```
   */
  env = {};
  #var;
  finalized = false;
  /**
   * `.error` can get the error object from the middleware if the Handler throws an error.
   *
   * @see {@link https://hono.dev/docs/api/context#error}
   *
   * @example
   * ```ts
   * app.use('*', async (c, next) => {
   *   await next()
   *   if (c.error) {
   *     // do something...
   *   }
   * })
   * ```
   */
  error;
  #status;
  #executionCtx;
  #res;
  #layout;
  #renderer;
  #notFoundHandler;
  #preparedHeaders;
  #matchResult;
  #path;
  /**
   * Creates an instance of the Context class.
   *
   * @param req - The Request object.
   * @param options - Optional configuration options for the context.
   */
  constructor(req, options) {
    this.#rawRequest = req;
    if (options) {
      this.#executionCtx = options.executionCtx;
      this.env = options.env;
      this.#notFoundHandler = options.notFoundHandler;
      this.#path = options.path;
      this.#matchResult = options.matchResult;
    }
  }
  /**
   * `.req` is the instance of {@link HonoRequest}.
   */
  get req() {
    this.#req ??= new HonoRequest(this.#rawRequest, this.#path, this.#matchResult);
    return this.#req;
  }
  /**
   * @see {@link https://hono.dev/docs/api/context#event}
   * The FetchEvent associated with the current request.
   *
   * @throws Will throw an error if the context does not have a FetchEvent.
   */
  get event() {
    if (this.#executionCtx && "respondWith" in this.#executionCtx) {
      return this.#executionCtx;
    } else {
      throw Error("This context has no FetchEvent");
    }
  }
  /**
   * @see {@link https://hono.dev/docs/api/context#executionctx}
   * The ExecutionContext associated with the current request.
   *
   * @throws Will throw an error if the context does not have an ExecutionContext.
   */
  get executionCtx() {
    if (this.#executionCtx) {
      return this.#executionCtx;
    } else {
      throw Error("This context has no ExecutionContext");
    }
  }
  /**
   * @see {@link https://hono.dev/docs/api/context#res}
   * The Response object for the current request.
   */
  get res() {
    return this.#res ||= createResponseInstance(null, {
      headers: this.#preparedHeaders ??= new Headers()
    });
  }
  /**
   * Sets the Response object for the current request.
   *
   * @param _res - The Response object to set.
   */
  set res(_res) {
    if (this.#res && _res) {
      _res = createResponseInstance(_res.body, _res);
      for (const [k, v] of this.#res.headers.entries()) {
        if (k === "content-type") {
          continue;
        }
        if (k === "set-cookie") {
          const cookies = this.#res.headers.getSetCookie();
          _res.headers.delete("set-cookie");
          for (const cookie of cookies) {
            _res.headers.append("set-cookie", cookie);
          }
        } else {
          _res.headers.set(k, v);
        }
      }
    }
    this.#res = _res;
    this.finalized = true;
  }
  /**
   * `.render()` can create a response within a layout.
   *
   * @see {@link https://hono.dev/docs/api/context#render-setrenderer}
   *
   * @example
   * ```ts
   * app.get('/', (c) => {
   *   return c.render('Hello!')
   * })
   * ```
   */
  render = (...args) => {
    this.#renderer ??= (content) => this.html(content);
    return this.#renderer(...args);
  };
  /**
   * Sets the layout for the response.
   *
   * @param layout - The layout to set.
   * @returns The layout function.
   */
  setLayout = (layout) => this.#layout = layout;
  /**
   * Gets the current layout for the response.
   *
   * @returns The current layout function.
   */
  getLayout = () => this.#layout;
  /**
   * `.setRenderer()` can set the layout in the custom middleware.
   *
   * @see {@link https://hono.dev/docs/api/context#render-setrenderer}
   *
   * @example
   * ```tsx
   * app.use('*', async (c, next) => {
   *   c.setRenderer((content) => {
   *     return c.html(
   *       <html>
   *         <body>
   *           <p>{content}</p>
   *         </body>
   *       </html>
   *     )
   *   })
   *   await next()
   * })
   * ```
   */
  setRenderer = (renderer) => {
    this.#renderer = renderer;
  };
  /**
   * `.header()` can set headers.
   *
   * @see {@link https://hono.dev/docs/api/context#header}
   *
   * @example
   * ```ts
   * app.get('/welcome', (c) => {
   *   // Set headers
   *   c.header('X-Message', 'Hello!')
   *   c.header('Content-Type', 'text/plain')
   *
   *   // Append multiple headers using the append option (e.g. Vary)
   *   c.header('Vary', 'Accept-Encoding', { append: true })
   *   c.header('Vary', 'User-Agent', { append: true })
   *
   *   return c.body('Thank you for coming')
   * })
   * ```
   */
  header = (name, value, options) => {
    if (this.finalized) {
      this.#res = createResponseInstance(this.#res.body, this.#res);
    }
    const headers = this.#res ? this.#res.headers : this.#preparedHeaders ??= new Headers();
    if (value === void 0) {
      headers.delete(name);
    } else if (options?.append) {
      headers.append(name, value);
    } else {
      headers.set(name, value);
    }
  };
  status = (status) => {
    this.#status = status;
  };
  /**
   * `.set()` can set the value specified by the key.
   *
   * @see {@link https://hono.dev/docs/api/context#set-get}
   *
   * @example
   * ```ts
   * app.use('*', async (c, next) => {
   *   c.set('message', 'Hono is hot!!')
   *   await next()
   * })
   * ```
   */
  set = (key, value) => {
    this.#var ??= /* @__PURE__ */ new Map();
    this.#var.set(key, value);
  };
  /**
   * `.get()` can use the value specified by the key.
   *
   * @see {@link https://hono.dev/docs/api/context#set-get}
   *
   * @example
   * ```ts
   * app.get('/', (c) => {
   *   const message = c.get('message')
   *   return c.text(`The message is "${message}"`)
   * })
   * ```
   */
  get = (key) => {
    return this.#var ? this.#var.get(key) : void 0;
  };
  /**
   * `.var` can access the value of a variable.
   *
   * @see {@link https://hono.dev/docs/api/context#var}
   *
   * @example
   * ```ts
   * const result = c.var.client.oneMethod()
   * ```
   */
  // c.var.propName is a read-only
  get var() {
    if (!this.#var) {
      return {};
    }
    return Object.fromEntries(this.#var);
  }
  #newResponse(data, arg, headers) {
    let responseHeaders = this.#res ? new Headers(this.#res.headers) : this.#preparedHeaders;
    if (typeof arg === "object" && arg.headers) {
      responseHeaders ??= new Headers();
      for (const [key, value] of new Headers(arg.headers)) {
        if (key === "set-cookie") {
          responseHeaders.append(key, value);
        } else {
          responseHeaders.set(key, value);
        }
      }
    }
    if (headers) {
      if (!responseHeaders) {
        let count = 0;
        for (const k in headers) {
          if (++count > 1 || typeof headers[k] !== "string") {
            responseHeaders = new Headers();
            break;
          }
        }
      }
      if (responseHeaders) {
        for (const k in headers) {
          const v = headers[k];
          if (typeof v === "string") {
            responseHeaders.set(k, v);
          } else {
            responseHeaders.delete(k);
            for (const v2 of v) {
              responseHeaders.append(k, v2);
            }
          }
        }
      }
    }
    const status = typeof arg === "number" ? arg : arg?.status ?? this.#status;
    return createResponseInstance(data, {
      status,
      headers: responseHeaders ?? headers
    });
  }
  newResponse = (...args) => this.#newResponse(...args);
  /**
   * `.body()` can return the HTTP response.
   * You can set headers with `.header()` and set HTTP status code with `.status`.
   * This can also be set in `.text()`, `.json()` and so on.
   *
   * @see {@link https://hono.dev/docs/api/context#body}
   *
   * @example
   * ```ts
   * app.get('/welcome', (c) => {
   *   // Set headers
   *   c.header('X-Message', 'Hello!')
   *   c.header('Content-Type', 'text/plain')
   *   // Set HTTP status code
   *   c.status(201)
   *
   *   // Return the response body
   *   return c.body('Thank you for coming')
   * })
   * ```
   */
  body = (data, arg, headers) => this.#newResponse(data, arg, headers);
  /**
   * `.text()` can render text as `Content-Type:text/plain`.
   *
   * @see {@link https://hono.dev/docs/api/context#text}
   *
   * @example
   * ```ts
   * app.get('/say', (c) => {
   *   return c.text('Hello!')
   * })
   * ```
   */
  text = (text, arg, headers) => {
    return !this.#preparedHeaders && !this.#status && !arg && !headers && !this.finalized ? new Response(text) : this.#newResponse(
      text,
      arg,
      setDefaultContentType(TEXT_PLAIN, headers)
    );
  };
  /**
   * `.json()` can render JSON as `Content-Type:application/json`.
   *
   * @see {@link https://hono.dev/docs/api/context#json}
   *
   * @example
   * ```ts
   * app.get('/api', (c) => {
   *   return c.json({ message: 'Hello!' })
   * })
   * ```
   */
  json = (object, arg, headers) => {
    return this.#newResponse(
      JSON.stringify(object),
      arg,
      setDefaultContentType("application/json", headers)
    );
  };
  html = (html, arg, headers) => {
    const res = (html2) => this.#newResponse(html2, arg, setDefaultContentType("text/html; charset=UTF-8", headers));
    return typeof html === "object" ? resolveCallback(html, HtmlEscapedCallbackPhase.Stringify, false, {}).then(res) : res(html);
  };
  /**
   * `.redirect()` can Redirect, default status code is 302.
   *
   * @see {@link https://hono.dev/docs/api/context#redirect}
   *
   * @example
   * ```ts
   * app.get('/redirect', (c) => {
   *   return c.redirect('/')
   * })
   * app.get('/redirect-permanently', (c) => {
   *   return c.redirect('/', 301)
   * })
   * ```
   */
  redirect = (location, status) => {
    const locationString = String(location);
    this.header(
      "Location",
      // Multibytes should be encoded
      // eslint-disable-next-line no-control-regex
      !/[^\x00-\xFF]/.test(locationString) ? locationString : encodeURI(locationString)
    );
    return this.newResponse(null, status ?? 302);
  };
  /**
   * `.notFound()` can return the Not Found Response.
   *
   * @see {@link https://hono.dev/docs/api/context#notfound}
   *
   * @example
   * ```ts
   * app.get('/notfound', (c) => {
   *   return c.notFound()
   * })
   * ```
   */
  notFound = () => {
    this.#notFoundHandler ??= () => createResponseInstance();
    return this.#notFoundHandler(this);
  };
};

// node_modules/hono/dist/router.js
var METHOD_NAME_ALL = "ALL";
var METHOD_NAME_ALL_LOWERCASE = "all";
var METHODS = ["get", "post", "put", "delete", "options", "patch", "query"];
var MESSAGE_MATCHER_IS_ALREADY_BUILT = "Can not add a route since the matcher is already built.";
var UnsupportedPathError = class extends Error {
};

// node_modules/hono/dist/utils/constants.js
var COMPOSED_HANDLER = "__COMPOSED_HANDLER";

// node_modules/hono/dist/hono-base.js
var notFoundHandler = (c) => {
  return c.text("404 Not Found", 404);
};
var errorHandler = (err, c) => {
  if ("getResponse" in err) {
    const res = err.getResponse();
    return c.newResponse(res.body, res);
  }
  console.error(err);
  return c.text("Internal Server Error", 500);
};
var Hono = class _Hono {
  get;
  post;
  put;
  delete;
  options;
  patch;
  query;
  all;
  on;
  use;
  /*
    This class is like an abstract class and does not have a router.
    To use it, inherit the class and implement router in the constructor.
  */
  router;
  getPath;
  // Cannot use `#` because it requires visibility at JavaScript runtime.
  _basePath = "/";
  #path = "/";
  routes = [];
  constructor(options = {}) {
    const allMethods = [...METHODS, METHOD_NAME_ALL_LOWERCASE];
    allMethods.forEach((method) => {
      this[method] = (args1, ...args) => {
        const methodName = method.toUpperCase();
        if (typeof args1 === "string") {
          this.#path = args1;
        } else {
          this.#addRoute(methodName, this.#path, args1);
        }
        args.forEach((handler) => {
          this.#addRoute(methodName, this.#path, handler);
        });
        return this;
      };
    });
    this.on = (method, path, ...handlers) => {
      for (const p of [path].flat()) {
        this.#path = p;
        for (const m of [method].flat()) {
          const methodName = m.toUpperCase();
          for (const handler of handlers) {
            this.#addRoute(methodName, this.#path, handler);
          }
        }
      }
      return this;
    };
    this.use = (arg1, ...handlers) => {
      if (typeof arg1 === "string") {
        this.#path = arg1;
      } else {
        this.#path = "*";
        handlers.unshift(arg1);
      }
      handlers.forEach((handler) => {
        this.#addRoute(METHOD_NAME_ALL, this.#path, handler);
      });
      return this;
    };
    const { strict, ...optionsWithoutStrict } = options;
    Object.assign(this, optionsWithoutStrict);
    this.getPath = strict ?? true ? options.getPath ?? getPath : getPathNoStrict;
  }
  #clone() {
    const clone = new _Hono({
      router: this.router,
      getPath: this.getPath
    });
    clone.errorHandler = this.errorHandler;
    clone.#notFoundHandler = this.#notFoundHandler;
    clone.routes = this.routes;
    return clone;
  }
  #notFoundHandler = notFoundHandler;
  // Cannot use `#` because it requires visibility at JavaScript runtime.
  errorHandler = errorHandler;
  /**
   * `.route()` allows grouping other Hono instance in routes.
   *
   * @see {@link https://hono.dev/docs/api/routing#grouping}
   *
   * @param {string} path - base Path
   * @param {Hono} app - other Hono instance
   * @returns {Hono} routed Hono instance
   *
   * @example
   * ```ts
   * const app = new Hono()
   * const app2 = new Hono()
   *
   * app2.get("/user", (c) => c.text("user"))
   * app.route("/api", app2) // GET /api/user
   * ```
   */
  route(path, app2) {
    const subApp = this.basePath(path);
    app2.routes.map((r) => {
      let handler;
      if (app2.errorHandler === errorHandler) {
        handler = r.handler;
      } else {
        handler = async (c, next) => (await compose([], app2.errorHandler)(c, () => r.handler(c, next))).res;
        handler[COMPOSED_HANDLER] = r.handler;
      }
      subApp.#addRoute(r.method, r.path, handler, r.basePath);
    });
    return this;
  }
  /**
   * `.basePath()` allows base paths to be specified.
   *
   * @see {@link https://hono.dev/docs/api/routing#base-path}
   *
   * @param {string} path - base Path
   * @returns {Hono} changed Hono instance
   *
   * @example
   * ```ts
   * const api = new Hono().basePath('/api')
   * ```
   */
  basePath(path) {
    const subApp = this.#clone();
    subApp._basePath = mergePath(this._basePath, path);
    return subApp;
  }
  /**
   * `.onError()` handles an error and returns a customized Response.
   *
   * @see {@link https://hono.dev/docs/api/hono#error-handling}
   *
   * @param {ErrorHandler} handler - request Handler for error
   * @returns {Hono} changed Hono instance
   *
   * @example
   * ```ts
   * app.onError((err, c) => {
   *   console.error(`${err}`)
   *   return c.text('Custom Error Message', 500)
   * })
   * ```
   */
  onError = (handler) => {
    this.errorHandler = handler;
    return this;
  };
  /**
   * `.notFound()` allows you to customize a Not Found Response.
   *
   * @see {@link https://hono.dev/docs/api/hono#not-found}
   *
   * @param {NotFoundHandler} handler - request handler for not-found
   * @returns {Hono} changed Hono instance
   *
   * @example
   * ```ts
   * app.notFound((c) => {
   *   return c.text('Custom 404 Message', 404)
   * })
   * ```
   */
  notFound = (handler) => {
    this.#notFoundHandler = handler;
    return this;
  };
  /**
   * `.mount()` allows you to mount applications built with other frameworks into your Hono application.
   *
   * @see {@link https://hono.dev/docs/api/hono#mount}
   *
   * @param {string} path - base Path
   * @param {Function} applicationHandler - other Request Handler
   * @param {MountOptions} [options] - options of `.mount()`
   * @returns {Hono} mounted Hono instance
   *
   * @example
   * ```ts
   * import { Router as IttyRouter } from 'itty-router'
   * import { Hono } from 'hono'
   * // Create itty-router application
   * const ittyRouter = IttyRouter()
   * // GET /itty-router/hello
   * ittyRouter.get('/hello', () => new Response('Hello from itty-router'))
   *
   * const app = new Hono()
   * app.mount('/itty-router', ittyRouter.handle)
   * ```
   *
   * @example
   * ```ts
   * const app = new Hono()
   * // Send the request to another application without modification.
   * app.mount('/app', anotherApp, {
   *   replaceRequest: (req) => req,
   * })
   * ```
   */
  mount(path, applicationHandler, options) {
    let replaceRequest;
    let optionHandler;
    if (options) {
      if (typeof options === "function") {
        optionHandler = options;
      } else {
        optionHandler = options.optionHandler;
        if (options.replaceRequest === false) {
          replaceRequest = (request) => request;
        } else {
          replaceRequest = options.replaceRequest;
        }
      }
    }
    const getOptions = optionHandler ? (c) => {
      const options2 = optionHandler(c);
      return Array.isArray(options2) ? options2 : [options2];
    } : (c) => {
      let executionContext = void 0;
      try {
        executionContext = c.executionCtx;
      } catch {
      }
      return [c.env, executionContext];
    };
    replaceRequest ||= (() => {
      const mergedPath = mergePath(this._basePath, path);
      const pathPrefixLength = mergedPath === "/" ? 0 : mergedPath.length;
      return (request) => {
        const url2 = new URL(request.url);
        url2.pathname = this.getPath(request).slice(pathPrefixLength) || "/";
        return new Request(url2, request);
      };
    })();
    const handler = async (c, next) => {
      const res = await applicationHandler(replaceRequest(c.req.raw), ...getOptions(c));
      if (res) {
        return res;
      }
      await next();
    };
    this.#addRoute(METHOD_NAME_ALL, mergePath(path, "*"), handler);
    return this;
  }
  #addRoute(method, path, handler, baseRoutePath) {
    path = mergePath(this._basePath, path);
    const r = {
      basePath: baseRoutePath !== void 0 ? mergePath(this._basePath, baseRoutePath) : this._basePath,
      path,
      method,
      handler
    };
    this.router.add(method, path, [handler, r]);
    this.routes.push(r);
  }
  #handleError(err, c) {
    if (err instanceof Error) {
      return this.errorHandler(err, c);
    }
    throw err;
  }
  #dispatch(request, executionCtx, env, method) {
    if (method === "HEAD") {
      return (async () => new Response(null, await this.#dispatch(request, executionCtx, env, "GET")))();
    }
    const path = this.getPath(request, { env });
    const matchResult = this.router.match(method, path);
    const c = new Context(request, {
      path,
      matchResult,
      env,
      executionCtx,
      notFoundHandler: this.#notFoundHandler
    });
    if (matchResult[0].length === 1) {
      let res;
      try {
        res = matchResult[0][0][0][0](c, async () => {
          c.res = await this.#notFoundHandler(c);
        });
      } catch (err) {
        return this.#handleError(err, c);
      }
      return res instanceof Promise ? res.then(
        (resolved) => resolved || (c.finalized ? c.res : this.#notFoundHandler(c))
      ).catch((err) => this.#handleError(err, c)) : res ?? this.#notFoundHandler(c);
    }
    const composed = compose(matchResult[0], this.errorHandler, this.#notFoundHandler);
    return (async () => {
      try {
        const context = await composed(c);
        if (!context.finalized) {
          throw new Error(
            "Context is not finalized. Did you forget to return a Response object or `await next()`?"
          );
        }
        return context.res;
      } catch (err) {
        return this.#handleError(err, c);
      }
    })();
  }
  /**
   * `.fetch()` will be entry point of your app.
   *
   * @see {@link https://hono.dev/docs/api/hono#fetch}
   *
   * @param {Request} request - request Object of request
   * @param {Env} env - env Object
   * @param {ExecutionContext} executionCtx - context of execution
   * @returns {Response | Promise<Response>} response of request
   *
   */
  fetch = (request, ...rest) => {
    return this.#dispatch(request, rest[1], rest[0], request.method);
  };
  /**
   * `.request()` is a useful method for testing.
   * You can pass a URL or pathname to send a GET request.
   * app will return a Response object.
   * ```ts
   * test('GET /hello is ok', async () => {
   *   const res = await app.request('/hello')
   *   expect(res.status).toBe(200)
   * })
   * ```
   * @see https://hono.dev/docs/api/hono#request
   */
  request = (input, requestInit, Env, executionCtx) => {
    if (input instanceof Request) {
      return this.fetch(requestInit ? new Request(input, requestInit) : input, Env, executionCtx);
    }
    input = input.toString();
    return this.fetch(
      new Request(
        /^https?:\/\//.test(input) ? input : `http://localhost${mergePath("/", input)}`,
        requestInit
      ),
      Env,
      executionCtx
    );
  };
  /**
   * `.fire()` automatically adds a global fetch event listener.
   * This can be useful for environments that adhere to the Service Worker API, such as non-ES module Cloudflare Workers.
   * @deprecated
   * Use `fire` from `hono/service-worker` instead.
   * ```ts
   * import { Hono } from 'hono'
   * import { fire } from 'hono/service-worker'
   *
   * const app = new Hono()
   * // ...
   * fire(app)
   * ```
   * @see https://hono.dev/docs/api/hono#fire
   * @see https://developer.mozilla.org/en-US/docs/Web/API/Service_Worker_API
   * @see https://developers.cloudflare.com/workers/reference/migrate-to-module-workers/
   */
  fire = () => {
    addEventListener("fetch", (event) => {
      event.respondWith(this.#dispatch(event.request, event, void 0, event.request.method));
    });
  };
};

// node_modules/hono/dist/router/utils.js
var createNullObject = () => /* @__PURE__ */ Object.create(null);

// node_modules/hono/dist/router/reg-exp-router/matcher.js
var emptyParam = [];
function match(method, path) {
  const matchers = this.buildAllMatchers();
  const match2 = ((method2, path2) => {
    const matcher = matchers[method2] || matchers[METHOD_NAME_ALL];
    const staticMatch = matcher[2][path2];
    if (staticMatch) {
      return staticMatch;
    }
    const match3 = path2.match(matcher[0]);
    if (!match3) {
      return [[], emptyParam];
    }
    const index = match3.indexOf("", 1);
    return [matcher[1][index], match3];
  });
  this.match = match2;
  return match2(method, path);
}

// node_modules/hono/dist/router/reg-exp-router/node.js
var LABEL_REG_EXP_STR = "[^/]+";
var ONLY_WILDCARD_REG_EXP_STR = ".*";
var TAIL_WILDCARD_REG_EXP_STR = "(?:|/.*)";
var PATH_ERROR = /* @__PURE__ */ Symbol();
var regExpMetaChars = new Set(".\\+*[^]$()");
function compareKey(a, b) {
  if (a.length === 1) {
    return b.length === 1 ? a < b ? -1 : 1 : -1;
  }
  if (b.length === 1) {
    return 1;
  }
  if (a === ONLY_WILDCARD_REG_EXP_STR || a === TAIL_WILDCARD_REG_EXP_STR) {
    return b === TAIL_WILDCARD_REG_EXP_STR ? -1 : 1;
  } else if (b === ONLY_WILDCARD_REG_EXP_STR || b === TAIL_WILDCARD_REG_EXP_STR) {
    return -1;
  }
  if (a === LABEL_REG_EXP_STR) {
    return 1;
  } else if (b === LABEL_REG_EXP_STR) {
    return -1;
  }
  return a.length === b.length ? a < b ? -1 : 1 : b.length - a.length;
}
var Node = class _Node {
  // handler index of a dynamic path, or -1 for a static path terminal
  #index;
  #varIndex;
  #children = createNullObject();
  insert(tokens, index, paramMap, context, isStatic) {
    let node = this;
    for (let i = 0, len = tokens.length; i < len; i++) {
      const token = tokens[i];
      const pattern = token.length === 1 ? token === "*" ? i === len - 1 ? ["", "", ONLY_WILDCARD_REG_EXP_STR] : ["", "", LABEL_REG_EXP_STR] : null : token === "/*" ? ["", "", TAIL_WILDCARD_REG_EXP_STR] : token.match(/^\:([^\{\}]+)(?:\{(.+)\})?$/);
      let nextNode;
      if (pattern) {
        const name = pattern[1];
        let regexpStr = pattern[2] || LABEL_REG_EXP_STR;
        if (name && pattern[2]) {
          if (regexpStr === ".*") {
            throw PATH_ERROR;
          }
          regexpStr = regexpStr.replace(/^\((?!\?:)(?=[^)]+\)$)/, "(?:");
          if (/\((?!\?:)/.test(regexpStr)) {
            throw PATH_ERROR;
          }
          if (regexpStr.length === 1 && regExpMetaChars.has(regexpStr)) {
            throw PATH_ERROR;
          }
        }
        nextNode = node.#children[regexpStr];
        if (!nextNode) {
          if (regexpStr !== ONLY_WILDCARD_REG_EXP_STR && regexpStr !== TAIL_WILDCARD_REG_EXP_STR) {
            for (const k in node.#children) {
              if (
                // a single-char pattern coexists with single-char literals as a literal does
                (regexpStr.length > 1 || k.length > 1) && k !== ONLY_WILDCARD_REG_EXP_STR && k !== TAIL_WILDCARD_REG_EXP_STR
              ) {
                throw PATH_ERROR;
              }
            }
          }
          nextNode = node.#children[regexpStr] = new _Node();
        }
        if (name !== "") {
          nextNode.#varIndex ??= context.varIndex++;
          paramMap.push([name, nextNode.#varIndex]);
        }
      } else {
        nextNode = node.#children[token];
        if (!nextNode) {
          for (const k in node.#children) {
            if (k.length > 1 && k !== ONLY_WILDCARD_REG_EXP_STR && k !== TAIL_WILDCARD_REG_EXP_STR) {
              throw PATH_ERROR;
            }
          }
          nextNode = node.#children[token] = new _Node();
        }
      }
      node = nextNode;
    }
    if (node.#index !== void 0) {
      throw PATH_ERROR;
    }
    node.#index = isStatic ? -1 : index;
  }
  buildRegExpStr() {
    const childKeys = Object.keys(this.#children).sort(compareKey);
    const strList = childKeys.map((k) => {
      const c = this.#children[k];
      const childStr = c.buildRegExpStr();
      return childStr === "" ? "" : (typeof c.#varIndex === "number" ? `(${k})@${c.#varIndex}` : regExpMetaChars.has(k) ? `\\${k}` : k) + childStr;
    }).filter(Boolean);
    if (typeof this.#index === "number" && this.#index !== -1) {
      strList.unshift(`#${this.#index}`);
    }
    if (strList.length === 0) {
      return "";
    }
    if (strList.length === 1) {
      return strList[0];
    }
    return "(?:" + strList.join("|") + ")";
  }
};

// node_modules/hono/dist/router/reg-exp-router/trie.js
var Trie = class {
  #context = { varIndex: 0 };
  #root = new Node();
  #index = 0;
  // dynamic path -> [handler index, param assoc]; static paths are not registered
  paths = createNullObject();
  insert(path, isStatic) {
    if (isStatic) {
      this.#root.insert(path.split(""), 0, [], this.#context, true);
      return;
    }
    const paramAssoc = [];
    const groups = [];
    let markedPath = path;
    for (let i = 0; ; ) {
      let replaced = false;
      markedPath = markedPath.replace(/\{[^}]+\}/g, (m) => {
        const mark = `@\\${i}`;
        groups[i] = [mark, m];
        i++;
        replaced = true;
        return mark;
      });
      if (!replaced) {
        break;
      }
    }
    const tokens = markedPath.match(/(?::[^\/]+)|(?:\/\*$)|./g) || [];
    for (let i = groups.length - 1; i >= 0; i--) {
      const [mark] = groups[i];
      for (let j = tokens.length - 1; j >= 0; j--) {
        if (tokens[j].indexOf(mark) !== -1) {
          tokens[j] = tokens[j].replace(mark, groups[i][1]);
          break;
        }
      }
    }
    this.#root.insert(tokens, this.#index, paramAssoc, this.#context, false);
    this.paths[path] = [this.#index++, paramAssoc];
  }
  buildRegExp() {
    let regexp = this.#root.buildRegExpStr();
    if (regexp === "") {
      return [/^$/, [], []];
    }
    let captureIndex = 0;
    const indexReplacementMap = [];
    const paramReplacementMap = [];
    regexp = regexp.replace(/#(\d+)|@(\d+)|\.\*\$/g, (_, handlerIndex, paramIndex) => {
      if (handlerIndex !== void 0) {
        indexReplacementMap[++captureIndex] = Number(handlerIndex);
        return "$()";
      }
      if (paramIndex !== void 0) {
        paramReplacementMap[Number(paramIndex)] = ++captureIndex;
        return "";
      }
      return "";
    });
    return [new RegExp(`^${regexp}`), indexReplacementMap, paramReplacementMap];
  }
};

// node_modules/hono/dist/router/reg-exp-router/router.js
var wildcardRegExpCache = createNullObject();
function buildWildcardRegExp(path) {
  return wildcardRegExpCache[path] ??= new RegExp(
    `^${path.replace(
      /\/:[^/{}]+(?:\{\[\^\/]\+})?(?=[/{]|$)|\/?\*$|([.\\+*[^\]$()?{}|])/g,
      (match2, metaChar) => metaChar ? `\\${metaChar}` : match2 === "/*" ? TAIL_WILDCARD_REG_EXP_STR : match2 === "*" ? ONLY_WILDCARD_REG_EXP_STR : `/:${LABEL_REG_EXP_STR}`
    )}$`
  );
}
function findMiddleware(middleware, path) {
  for (const k of Object.keys(middleware).sort((a, b) => b.length - a.length)) {
    if (buildWildcardRegExp(k).test(path)) {
      return [...middleware[k]];
    }
  }
  return void 0;
}
var RegExpRouter = class {
  name = "RegExpRouter";
  #middleware;
  #routes;
  #tries;
  constructor() {
    this.#middleware = { [METHOD_NAME_ALL]: createNullObject() };
    this.#routes = { [METHOD_NAME_ALL]: createNullObject() };
    this.#tries = { [METHOD_NAME_ALL]: new Trie() };
  }
  #insertPath(method, path) {
    try {
      this.#tries[method].insert(path, !/\*|\/:/.test(path));
    } catch (e) {
      throw e === PATH_ERROR ? new UnsupportedPathError(path) : e;
    }
  }
  add(method, path, handler) {
    const middleware = this.#middleware;
    const routes = this.#routes;
    if (!middleware) {
      throw new Error(MESSAGE_MATCHER_IS_ALREADY_BUILT);
    }
    if (!middleware[method]) {
      this.#tries[method] = new Trie();
      for (const handlerMap of [middleware, routes]) {
        handlerMap[method] = createNullObject();
        for (const p in handlerMap[METHOD_NAME_ALL]) {
          handlerMap[method][p] = [...handlerMap[METHOD_NAME_ALL][p]];
          this.#insertPath(method, p);
        }
      }
    }
    if (path === "/*") {
      path = "*";
    }
    const methods = method === METHOD_NAME_ALL ? Object.keys(middleware) : [method];
    if (/\*$/.test(path)) {
      const re = buildWildcardRegExp(path);
      for (const m of methods) {
        if (!middleware[m][path]) {
          this.#insertPath(m, path);
          middleware[m][path] = findMiddleware(middleware[m], path) || findMiddleware(middleware[METHOD_NAME_ALL], path) || [];
        }
      }
      for (const handlerMap of [middleware, routes]) {
        for (const m of methods) {
          for (const p in handlerMap[m]) {
            re.test(p) && handlerMap[m][p].push([handler, path]);
          }
        }
      }
      return;
    }
    const paths = checkOptionalParameter(path) || [path];
    for (const path2 of paths) {
      for (const m of methods) {
        if (!routes[m][path2]) {
          this.#insertPath(m, path2);
          routes[m][path2] = findMiddleware(middleware[m], path2) || findMiddleware(middleware[METHOD_NAME_ALL], path2) || [];
        }
        routes[m][path2].push([handler, path2]);
      }
    }
  }
  match = match;
  buildAllMatchers() {
    const matchers = createNullObject();
    for (const method of Object.keys(this.#routes)) {
      matchers[method] = this.#buildMatcher(method);
    }
    this.#middleware = this.#routes = this.#tries = void 0;
    wildcardRegExpCache = createNullObject();
    return matchers;
  }
  #buildMatcher(method) {
    const middleware = this.#middleware[method];
    const routes = this.#routes[method];
    const trie = this.#tries[method];
    const staticMap = createNullObject();
    const handlerData = [];
    const [regexp, indexReplacementMap, paramReplacementMap] = trie.buildRegExp();
    for (const r of [middleware, routes]) {
      for (const path in r) {
        const handlers = r[path];
        const pathData = trie.paths[path];
        if (!pathData) {
          staticMap[path] = [handlers.map(([h]) => [h, createNullObject()]), emptyParam];
          continue;
        }
        handlerData[pathData[0]] = handlers.map(([h, handlerPath]) => [
          h,
          trie.paths[handlerPath][1].reduceRight((map, [key], i) => {
            map[key] = paramReplacementMap[pathData[1][i][1]];
            return map;
          }, createNullObject())
        ]);
      }
    }
    return [regexp, indexReplacementMap.map((i) => handlerData[i]), staticMap];
  }
};

// node_modules/hono/dist/router/smart-router/router.js
var SmartRouter = class {
  name = "SmartRouter";
  #routers = [];
  #routes = [];
  constructor(init) {
    this.#routers = init.routers;
  }
  add(method, path, handler) {
    if (!this.#routes) {
      throw new Error(MESSAGE_MATCHER_IS_ALREADY_BUILT);
    }
    this.#routes.push([method, path, handler]);
  }
  match(method, path) {
    if (!this.#routes) {
      throw new Error("Fatal error");
    }
    const routers = this.#routers;
    const routes = this.#routes;
    const len = routers.length;
    let i = 0;
    let res;
    for (; i < len; i++) {
      const router = routers[i];
      try {
        for (let i2 = 0, len2 = routes.length; i2 < len2; i2++) {
          router.add(...routes[i2]);
        }
        res = router.match(method, path);
      } catch (e) {
        if (e instanceof UnsupportedPathError) {
          continue;
        }
        throw e;
      }
      this.match = router.match.bind(router);
      this.#routers = [router];
      this.#routes = void 0;
      break;
    }
    if (i === len) {
      throw new Error("Fatal error");
    }
    this.name = `SmartRouter + ${this.activeRouter.name}`;
    return res;
  }
  get activeRouter() {
    if (this.#routes || this.#routers.length !== 1) {
      throw new Error("No active router has been determined yet.");
    }
    return this.#routers[0];
  }
};

// node_modules/hono/dist/router/trie-router/node.js
var emptyParams = createNullObject();
var order = 0;
var Node2 = class _Node2 {
  #methods = [];
  #children = createNullObject();
  #patterns = [];
  #pattern;
  #params = emptyParams;
  insert(method, path, handler) {
    let curNode = this;
    const parts = splitRoutingPath(path);
    const possibleKeys = /* @__PURE__ */ new Set();
    let i = 0;
    for (const p of parts) {
      const nextP = parts[++i];
      const pattern = getPattern(p, nextP) || (nextP === void 0 && p && p.indexOf("*") === p.length - 1 ? p : null);
      const isParam = Array.isArray(pattern);
      const key = isParam ? pattern[0] : pattern || p;
      const child = curNode.#children[key] ||= new _Node2();
      if (pattern && !child.#pattern) {
        child.#pattern = pattern;
        curNode.#patterns.push(child);
      }
      curNode = child;
      if (isParam) {
        possibleKeys.add(pattern[1]);
      }
    }
    curNode.#methods.push({
      [method]: {
        handler,
        possibleKeys: [...possibleKeys],
        score: ++order
      }
    });
  }
  #pushHandlerSets(handlerSets, node, method, nodeParams, params) {
    for (let i = 0, len = node.#methods.length; i < len; i++) {
      const m = node.#methods[i];
      const handlerSet = m[method] || m[METHOD_NAME_ALL];
      if (handlerSet) {
        handlerSet.params = createNullObject();
        handlerSets.push(handlerSet);
        for (let i2 = 0, len2 = handlerSet.possibleKeys.length; i2 < len2; i2++) {
          const key = handlerSet.possibleKeys[i2];
          handlerSet.params[key] = params?.[key] && !i2 ? params[key] : nodeParams[key] ?? params?.[key];
        }
      }
    }
  }
  search(method, path) {
    const handlerSets = [];
    this.#params = emptyParams;
    const curNode = this;
    let curNodes = [curNode];
    const parts = splitPath(path);
    const curNodesQueue = [];
    const len = parts.length;
    let partOffsets = null;
    for (let i = 0; i < len; i++) {
      const part = parts[i];
      const isLast = i === len - 1;
      const tempNodes = [];
      for (let j = 0, len2 = curNodes.length; j < len2; j++) {
        const node = curNodes[j];
        const nextNode = node.#children[part];
        if (nextNode) {
          nextNode.#params = node.#params;
          if (isLast) {
            if (nextNode.#children["*"]) {
              this.#pushHandlerSets(handlerSets, nextNode.#children["*"], method, node.#params);
            }
            this.#pushHandlerSets(handlerSets, nextNode, method, node.#params);
          } else {
            tempNodes.push(nextNode);
          }
        }
        for (const child of node.#patterns) {
          const pattern = child.#pattern;
          const params = node.#params === emptyParams ? {} : { ...node.#params };
          if (typeof pattern === "string") {
            if (pattern === "*" || part.startsWith(pattern.slice(0, -1))) {
              this.#pushHandlerSets(handlerSets, child, method, node.#params);
              if (pattern === "*") {
                child.#params = params;
                tempNodes.push(child);
              }
            }
            continue;
          }
          const [, name, matcher] = pattern;
          if (!part && matcher === true) {
            continue;
          }
          if (matcher !== true) {
            if (!partOffsets) {
              partOffsets = [];
              let offset = path[0] === "/" ? 1 : 0;
              for (let p = 0; p < len; p++) {
                partOffsets[p] = offset;
                offset += parts[p].length + 1;
              }
            }
            const restPathString = path.slice(partOffsets[i]);
            const m = matcher.exec(restPathString);
            if (m) {
              params[name] = m[0];
              this.#pushHandlerSets(handlerSets, child, method, node.#params, params);
              if (m[0].length === restPathString.length && child.#children["*"]) {
                this.#pushHandlerSets(
                  handlerSets,
                  child.#children["*"],
                  method,
                  node.#params,
                  params
                );
              }
              for (const _ in child.#children) {
                child.#params = params;
                const componentCount = m[0].match(/\//g)?.length ?? 0;
                const targetCurNodes = curNodesQueue[componentCount] ||= [];
                targetCurNodes.push(child);
                break;
              }
              continue;
            }
          }
          if (matcher === true || matcher.test(part)) {
            params[name] = part;
            if (isLast) {
              this.#pushHandlerSets(handlerSets, child, method, params, node.#params);
              if (child.#children["*"]) {
                this.#pushHandlerSets(
                  handlerSets,
                  child.#children["*"],
                  method,
                  params,
                  node.#params
                );
              }
            } else {
              child.#params = params;
              tempNodes.push(child);
            }
          }
        }
      }
      const shifted = curNodesQueue.shift();
      curNodes = shifted ? tempNodes.concat(shifted) : tempNodes;
    }
    if (handlerSets[1]) {
      handlerSets.sort((a, b) => {
        return a.score - b.score;
      });
    }
    return [handlerSets.map(({ handler, params }) => [handler, params])];
  }
};

// node_modules/hono/dist/router/trie-router/router.js
var TrieRouter = class {
  name = "TrieRouter";
  #node = new Node2();
  add(method, path, handler) {
    for (const result of checkOptionalParameter(path) || [path]) {
      this.#node.insert(method, result, handler);
    }
  }
  match(method, path) {
    return this.#node.search(method, path);
  }
};

// node_modules/hono/dist/hono.js
var Hono2 = class extends Hono {
  /**
   * Creates an instance of the Hono class.
   *
   * @param options - Optional configuration options for the Hono instance.
   */
  constructor(options = {}) {
    super(options);
    this.router = options.router ?? new SmartRouter({
      routers: [new RegExpRouter(), new TrieRouter()]
    });
  }
};

// node_modules/@hono/node-server/dist/constants-BLSFu_RU.mjs
var X_ALREADY_SENT = "x-hono-already-sent";

// node_modules/@hono/node-server/dist/index.mjs
import { STATUS_CODES, createServer } from "node:http";
import { Http2ServerRequest, constants } from "node:http2";
import { Readable } from "node:stream";

// node_modules/hono/dist/helper/websocket/index.js
var defineWebSocketHelper = (handler) => {
  return ((...args) => {
    if (typeof args[0] === "function") {
      const [createEvents, options] = args;
      return async function upgradeWebSocket2(c, next) {
        const events = await createEvents(c);
        const result = await handler(c, events, options);
        if (result) {
          return result;
        }
        await next();
      };
    } else {
      const [c, events, options] = args;
      return (async () => {
        const upgraded = await handler(c, events, options);
        if (!upgraded) {
          throw new Error("Failed to upgrade WebSocket");
        }
        return upgraded;
      })();
    }
  });
};

// node_modules/@hono/node-server/dist/index.mjs
var RequestError = class extends Error {
  constructor(message, options) {
    super(message, options);
    this.name = "RequestError";
  }
};
var nonJoinedHeaders = /* @__PURE__ */ new Set([
  "age",
  "authorization",
  "content-length",
  "content-type",
  "etag",
  "expires",
  "from",
  "host",
  "if-modified-since",
  "if-unmodified-since",
  "last-modified",
  "location",
  "max-forwards",
  "proxy-authorization",
  "referer",
  "retry-after",
  "server",
  "user-agent"
]);
var validHeaderName = /^[!#$%&'*+\-.^_`|~\dA-Za-z]+$/;
var isHttpWhitespace = (code) => code === 9 || code === 10 || code === 13 || code === 32;
var normalizeHeaderValue = (value) => {
  if (!isHttpWhitespace(value.charCodeAt(0)) && !isHttpWhitespace(value.charCodeAt(value.length - 1))) return value;
  let start = 0;
  let end = value.length;
  while (start < end && isHttpWhitespace(value.charCodeAt(start))) start++;
  while (end > start && isHttpWhitespace(value.charCodeAt(end - 1))) end--;
  return value.slice(start, end);
};
var forbiddenHeaderValue = /[\0\r\n]/;
var GlobalHeaders = globalThis.Headers;
var materializeHeaders = (rawHeaders, HeadersCtor = GlobalHeaders) => {
  const headers = new HeadersCtor();
  for (let i = 0; i < rawHeaders.length; i += 2) {
    const name = rawHeaders[i];
    if (!name.startsWith(":")) headers.append(name, rawHeaders[i + 1]);
  }
  return headers;
};
var RequestHeaders = class {
  #incoming;
  #rawHeaders;
  #headers;
  #invalidValue;
  constructor(incoming) {
    this.#incoming = incoming;
    if (incoming instanceof Http2ServerRequest) this.#rawHeaders = incoming.rawHeaders.slice();
  }
  get #lazyRawHeaders() {
    return this.#rawHeaders ??= this.#incoming.rawHeaders.slice();
  }
  get #native() {
    if (!this.#headers) {
      this.#headers = materializeHeaders(this.#lazyRawHeaders);
      this.#rawHeaders = void 0;
    }
    return this.#headers;
  }
  #normalizedName(name) {
    if (typeof name !== "string") return;
    if (!validHeaderName.test(name)) throw new TypeError(`Invalid header name: ${name}`);
    return name.toLowerCase();
  }
  #lookupHttp1(lowerName) {
    const headers = this.#incoming instanceof Http2ServerRequest ? void 0 : this.#incoming.headers;
    if (!headers || nonJoinedHeaders.has(lowerName) || lowerName === "set-cookie" || lowerName === "__proto__") return;
    if (!Object.hasOwn(headers, lowerName)) return null;
    const rawValue = headers[lowerName];
    if (typeof rawValue === "string") {
      const value = normalizeHeaderValue(rawValue);
      return forbiddenHeaderValue.test(value) ? void 0 : value;
    }
  }
  #lookup(rawHeaders, lowerName) {
    const separator = lowerName === "cookie" ? "; " : ", ";
    let value = null;
    for (let i = 0; i < rawHeaders.length; i += 2) {
      const rawName = rawHeaders[i];
      if (rawName.length === lowerName.length && rawName.toLowerCase() === lowerName) {
        const rawValue = normalizeHeaderValue(rawHeaders[i + 1]);
        if (forbiddenHeaderValue.test(rawValue)) {
          this.#invalidValue = true;
          return;
        }
        value = value === null ? rawValue : value + separator + rawValue;
      }
    }
    return value;
  }
  append(name, value) {
    this.#native.append(name, value);
  }
  delete(name) {
    this.#native.delete(name);
  }
  get(name) {
    const lowerName = this.#normalizedName(name);
    if (lowerName && !this.#headers && !this.#invalidValue) {
      const http1Value = this.#lookupHttp1(lowerName);
      if (http1Value !== void 0) return http1Value;
      const value = this.#lookup(this.#lazyRawHeaders, lowerName);
      if (value !== void 0) return value;
    }
    return this.#native.get(name);
  }
  has(name) {
    const lowerName = this.#normalizedName(name);
    if (lowerName && !this.#headers && !this.#invalidValue) {
      const http1Value = this.#lookupHttp1(lowerName);
      if (http1Value !== void 0) return http1Value !== null;
      const value = this.#lookup(this.#lazyRawHeaders, lowerName);
      if (value !== void 0) return value !== null;
    }
    return this.#native.has(name);
  }
  set(name, value) {
    this.#native.set(name, value);
  }
  getSetCookie() {
    return this.#native.getSetCookie();
  }
  keys() {
    return this.#native.keys();
  }
  values() {
    return this.#native.values();
  }
  entries() {
    return this.#native.entries();
  }
  forEach(callback, thisArg) {
    this.#native.forEach((value, key) => {
      callback.call(thisArg, value, key, this);
    });
  }
  [Symbol.iterator]() {
    return this.entries();
  }
};
Object.defineProperty(RequestHeaders.prototype, /* @__PURE__ */ Symbol.for("nodejs.util.inspect.custom"), { value: function(depth, options, inspectFn) {
  return `Headers (lightweight) ${inspectFn(Object.fromEntries(this), {
    ...options,
    depth: depth == null ? null : depth - 1
  })}`;
} });
Object.setPrototypeOf(RequestHeaders.prototype, GlobalHeaders.prototype);
var newHeadersFromIncoming = (incoming) => globalThis.Headers === GlobalHeaders ? new RequestHeaders(incoming) : materializeHeaders(incoming.rawHeaders, globalThis.Headers);
var reValidRequestUrl = /^\/[!#$&-;=?-\[\]_a-z~]*$/;
var reDotSegment = /\/\.\.?(?:[/?#]|$)/;
var reValidHost = /^[a-z0-9._-]+(?::(?:[1-5]\d{3,4}|[6-9]\d{3}))?$/;
var buildUrl = (scheme, host2, incomingUrl) => {
  const url2 = `${scheme}://${host2}${incomingUrl}`;
  if (!reValidHost.test(host2)) {
    const urlObj = new URL(url2);
    if (urlObj.hostname.length !== host2.length && urlObj.hostname !== (host2.includes(":") ? host2.replace(/:\d+$/, "") : host2).toLowerCase()) throw new RequestError("Invalid host header");
    return urlObj.href;
  } else if (incomingUrl.length === 0) return url2 + "/";
  else {
    if (incomingUrl.charCodeAt(0) !== 47) throw new RequestError("Invalid URL");
    if (!reValidRequestUrl.test(incomingUrl) || reDotSegment.test(incomingUrl)) return new URL(url2).href;
    return url2;
  }
};
var toRequestError = (e) => {
  if (e instanceof RequestError) return e;
  return new RequestError(e.message, { cause: e });
};
var GlobalRequest = global.Request;
var Request$1 = class extends GlobalRequest {
  constructor(input, options) {
    if (typeof input === "object" && getRequestCache in input) {
      const hasReplacementBody = options !== void 0 && "body" in options && options.body != null;
      if (input[bodyConsumedDirectlyKey] && !hasReplacementBody) throw new TypeError("Cannot construct a Request with a Request object that has already been used.");
      input = input[getRequestCache]();
    }
    if (typeof options?.body?.getReader !== "undefined") options.duplex ??= "half";
    super(input, options);
  }
};
var wrapBodyStream = /* @__PURE__ */ Symbol("wrapBodyStream");
var byteExactEncodings = /* @__PURE__ */ new Set([
  "latin1",
  "binary",
  "hex",
  "base64",
  "base64url"
]);
var isByteExactEncoding = (encoding) => encoding === null || byteExactEncodings.has(encoding);
var bodyBufferedBeforeDisconnectKey = /* @__PURE__ */ Symbol("bodyBufferedBeforeDisconnect");
var bodyBufferedLengthBeforeDisconnectKey = /* @__PURE__ */ Symbol("bodyBufferedLengthBeforeDisconnect");
var toBufferChunk = (chunk, encoding) => Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk, encoding ?? "utf8");
var isRecoverableDisconnectedIncoming = (incoming) => !(incoming instanceof Http2ServerRequest) && !!incoming.complete && !!incoming.readableAborted && typeof incoming.read === "function" && isByteExactEncoding(incoming.readableEncoding);
var recordBodyBufferedBeforeDisconnect = (incoming) => {
  if (incoming.readableDidRead || !isRecoverableDisconnectedIncoming(incoming)) return;
  const incomingWithRecovery = incoming;
  incomingWithRecovery[bodyBufferedLengthBeforeDisconnectKey] ??= incoming.readableLength;
};
var readBodyBufferedBeforeDisconnect = (incoming, chunks) => {
  if (incoming.readableDidRead && !chunks || !isRecoverableDisconnectedIncoming(incoming)) return;
  const incomingWithRecovery = incoming;
  if (incomingWithRecovery[bodyBufferedBeforeDisconnectKey] !== void 0) return incomingWithRecovery[bodyBufferedBeforeDisconnectKey];
  let result;
  const errored = incoming.errored;
  if (errored && errored.code !== "ECONNRESET") result = errored;
  else if (incomingWithRecovery[bodyBufferedLengthBeforeDisconnectKey] !== void 0 && incoming.readableLength !== incomingWithRecovery[bodyBufferedLengthBeforeDisconnectKey]) result = newBodyUnusableError();
  else {
    const bodyChunks = chunks ?? [];
    const chunk = incoming.read();
    if (chunk !== null) bodyChunks.push(toBufferChunk(chunk, incoming.readableEncoding));
    const buffer = bodyChunks.length === 1 ? bodyChunks[0] : Buffer.concat(bodyChunks);
    result = buffer;
    const contentLength = incoming.headers["content-length"];
    if (typeof contentLength === "string" && /^\d+$/.test(contentLength)) {
      const expectedLength = Number(contentLength);
      if (Number.isSafeInteger(expectedLength) && buffer.length !== expectedLength) result = newBodyUnusableError();
    }
  }
  incomingWithRecovery[bodyBufferedBeforeDisconnectKey] = result;
  return result;
};
var enqueueBufferedBody = (controller, buffered) => {
  if (buffered instanceof Error) {
    controller.error(buffered);
    return;
  }
  if (buffered.length > 0) controller.enqueue(buffered);
  controller.close();
};
var newRequestFromIncoming = (method, url2, headers, incoming, abortController) => {
  const init = {
    method,
    headers,
    signal: abortController.signal
  };
  if (method === "TRACE") {
    init.method = "GET";
    const req = new Request$1(url2, init);
    Object.defineProperty(req, "method", { get() {
      return "TRACE";
    } });
    return req;
  }
  if (!(method === "GET" || method === "HEAD")) if ("rawBody" in incoming && incoming.rawBody instanceof Buffer) init.body = new ReadableStream({ start(controller) {
    controller.enqueue(incoming.rawBody);
    controller.close();
  } });
  else if (incoming[wrapBodyStream]) {
    let reader;
    init.body = new ReadableStream({ async pull(controller) {
      try {
        if (!reader) {
          const buffered = readBodyBufferedBeforeDisconnect(incoming);
          if (buffered !== void 0) {
            enqueueBufferedBody(controller, buffered);
            return;
          }
        }
        reader ||= Readable.toWeb(incoming).getReader();
        const { done, value } = await reader.read();
        if (done) controller.close();
        else controller.enqueue(value);
      } catch (error) {
        controller.error(error);
      }
    } });
  } else {
    const buffered = readBodyBufferedBeforeDisconnect(incoming);
    if (buffered !== void 0) init.body = new ReadableStream({ start(controller) {
      enqueueBufferedBody(controller, buffered);
    } });
    else init.body = Readable.toWeb(incoming);
  }
  return new Request$1(url2, init);
};
var getRequestCache = /* @__PURE__ */ Symbol("getRequestCache");
var requestCache = /* @__PURE__ */ Symbol("requestCache");
var incomingKey = /* @__PURE__ */ Symbol("incomingKey");
var urlKey = /* @__PURE__ */ Symbol("urlKey");
var methodKey = /* @__PURE__ */ Symbol("methodKey");
var headersKey = /* @__PURE__ */ Symbol("headersKey");
var abortControllerKey = /* @__PURE__ */ Symbol("abortControllerKey");
var getAbortController = /* @__PURE__ */ Symbol("getAbortController");
var abortRequest = /* @__PURE__ */ Symbol("abortRequest");
var bodyBufferKey = /* @__PURE__ */ Symbol("bodyBuffer");
var bodyReadPromiseKey = /* @__PURE__ */ Symbol("bodyReadPromise");
var bodyConsumedDirectlyKey = /* @__PURE__ */ Symbol("bodyConsumedDirectly");
var bodyLockReaderKey = /* @__PURE__ */ Symbol("bodyLockReader");
var abortReasonKey = /* @__PURE__ */ Symbol("abortReason");
var newBodyUnusableError = () => {
  return /* @__PURE__ */ new TypeError("Body is unusable");
};
var rejectBodyUnusable = () => {
  return Promise.reject(newBodyUnusableError());
};
var textDecoder = new TextDecoder();
var consumeBodyDirectOnce = (request) => {
  if (request[bodyConsumedDirectlyKey]) return rejectBodyUnusable();
  request[bodyConsumedDirectlyKey] = true;
};
var toArrayBuffer = (buf) => {
  return buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);
};
var contentType = (request) => {
  return (request[headersKey] ||= newHeadersFromIncoming(request[incomingKey])).get("content-type") || "";
};
var methodTokenRegExp = /^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$/;
var normalizeIncomingMethod = (method) => {
  if (typeof method !== "string" || method.length === 0) return "GET";
  switch (method) {
    case "DELETE":
    case "GET":
    case "HEAD":
    case "OPTIONS":
    case "PATCH":
    case "POST":
    case "PUT":
    case "QUERY":
      return method;
  }
  const upper = method.toUpperCase();
  switch (upper) {
    case "DELETE":
    case "GET":
    case "HEAD":
    case "OPTIONS":
    case "POST":
    case "PUT":
      return upper;
    default:
      return method;
  }
};
var validateDirectReadMethod = (method) => {
  if (!methodTokenRegExp.test(method)) return /* @__PURE__ */ new TypeError(`'${method}' is not a valid HTTP method.`);
  const normalized = method.toUpperCase();
  if (normalized === "CONNECT" || normalized === "TRACK" || normalized === "TRACE" && method !== "TRACE") return /* @__PURE__ */ new TypeError(`'${method}' HTTP method is unsupported.`);
};
var readBodyWithFastPath = (request, method, fromBuffer) => {
  if (request[bodyConsumedDirectlyKey]) return rejectBodyUnusable();
  const methodName = request.method;
  if (methodName === "GET" || methodName === "HEAD") return request[getRequestCache]()[method]();
  const methodValidationError = validateDirectReadMethod(methodName);
  if (methodValidationError) return Promise.reject(methodValidationError);
  if (request[requestCache]) {
    if (methodName !== "TRACE") return request[requestCache][method]();
  }
  const alreadyUsedError = consumeBodyDirectOnce(request);
  if (alreadyUsedError) return alreadyUsedError;
  const raw2 = readRawBodyIfAvailable(request);
  if (raw2) {
    const result = Promise.resolve(fromBuffer(raw2, request));
    request[bodyBufferKey] = void 0;
    return result;
  }
  return readBodyDirect(request).then((buf) => {
    const result = fromBuffer(buf, request);
    request[bodyBufferKey] = void 0;
    return result;
  });
};
var readRawBodyIfAvailable = (request) => {
  const incoming = request[incomingKey];
  if ("rawBody" in incoming && incoming.rawBody instanceof Buffer) return incoming.rawBody;
};
var normalizeAbortError = (request, incoming) => {
  if (incoming.errored) return incoming.errored;
  const reason = request[abortReasonKey];
  if (reason !== void 0) return reason instanceof Error ? reason : new Error(String(reason));
  return /* @__PURE__ */ new Error("Client connection prematurely closed.");
};
var readBodyDirect = (request) => {
  if (request[bodyBufferKey]) return Promise.resolve(request[bodyBufferKey]);
  if (request[bodyReadPromiseKey]) return request[bodyReadPromiseKey];
  const incoming = request[incomingKey];
  if (incoming.readableDidRead) return rejectBodyUnusable();
  const buffered = readBodyBufferedBeforeDisconnect(incoming);
  if (buffered !== void 0) {
    if (buffered instanceof Error) return Promise.reject(buffered);
    request[bodyBufferKey] = buffered;
    return Promise.resolve(buffered);
  }
  const promise = new Promise((resolve3, reject) => {
    const chunks = [];
    let settled = false;
    const finish = (callback) => {
      if (settled) return;
      settled = true;
      cleanup();
      callback();
    };
    const recoverCompleteBodyAfterDisconnect = (error) => {
      const streamError = incoming.errored ?? error;
      if (!isRecoverableDisconnectedIncoming(incoming) || streamError && streamError.code !== "ECONNRESET") return false;
      finish(() => {
        const recovered = readBodyBufferedBeforeDisconnect(incoming, chunks);
        if (recovered instanceof Error) reject(recovered);
        else if (recovered === void 0) reject(error ?? normalizeAbortError(request, incoming));
        else {
          request[bodyBufferKey] = recovered;
          resolve3(recovered);
        }
      });
      return true;
    };
    const onData = (chunk) => {
      chunks.push(toBufferChunk(chunk, incoming.readableEncoding));
    };
    const onEnd = () => {
      finish(() => {
        const buffer = chunks.length === 1 ? chunks[0] : Buffer.concat(chunks);
        request[bodyBufferKey] = buffer;
        resolve3(buffer);
      });
    };
    const onError = (error) => {
      if (recoverCompleteBodyAfterDisconnect(error)) return;
      finish(() => {
        reject(error);
      });
    };
    const onClose = () => {
      if (incoming.readableEnded) {
        onEnd();
        return;
      }
      if (recoverCompleteBodyAfterDisconnect()) return;
      finish(() => {
        reject(normalizeAbortError(request, incoming));
      });
    };
    const cleanup = () => {
      incoming.off("data", onData);
      incoming.off("end", onEnd);
      incoming.off("error", onError);
      incoming.off("close", onClose);
      request[bodyReadPromiseKey] = void 0;
    };
    incoming.on("data", onData);
    incoming.on("end", onEnd);
    incoming.on("error", onError);
    incoming.on("close", onClose);
    queueMicrotask(() => {
      if (settled) return;
      if (incoming.readableEnded) onEnd();
      else if (incoming.errored) onError(incoming.errored);
      else if (incoming.destroyed) onClose();
    });
  });
  request[bodyReadPromiseKey] = promise;
  return promise;
};
var requestPrototype = {
  get method() {
    return this[methodKey];
  },
  get url() {
    return this[urlKey];
  },
  get headers() {
    return this[headersKey] ||= newHeadersFromIncoming(this[incomingKey]);
  },
  [abortRequest](reason) {
    if (this[abortReasonKey] === void 0) this[abortReasonKey] = reason;
    const abortController = this[abortControllerKey];
    if (abortController && !abortController.signal.aborted) abortController.abort(reason);
  },
  [getAbortController]() {
    this[abortControllerKey] ||= new AbortController();
    if (this[abortReasonKey] !== void 0 && !this[abortControllerKey].signal.aborted) this[abortControllerKey].abort(this[abortReasonKey]);
    return this[abortControllerKey];
  },
  [getRequestCache]() {
    const abortController = this[getAbortController]();
    if (this[requestCache]) return this[requestCache];
    const method = this.method;
    if (this[bodyConsumedDirectlyKey] && !(method === "GET" || method === "HEAD")) {
      this[bodyBufferKey] = void 0;
      const init = {
        method: method === "TRACE" ? "GET" : method,
        headers: this.headers,
        signal: abortController.signal
      };
      if (method !== "TRACE") {
        init.body = new ReadableStream({ start(c) {
          c.close();
        } });
        init.duplex = "half";
      }
      const req = new Request$1(this[urlKey], init);
      if (method === "TRACE") Object.defineProperty(req, "method", { get() {
        return "TRACE";
      } });
      return this[requestCache] = req;
    }
    return this[requestCache] = newRequestFromIncoming(this.method, this[urlKey], this.headers, this[incomingKey], abortController);
  },
  get body() {
    if (!this[bodyConsumedDirectlyKey]) return this[getRequestCache]().body;
    const request = this[getRequestCache]();
    if (!this[bodyLockReaderKey] && request.body) this[bodyLockReaderKey] = request.body.getReader();
    return request.body;
  },
  get bodyUsed() {
    if (this[bodyConsumedDirectlyKey]) return true;
    if (this[requestCache]) return this[requestCache].bodyUsed;
    return false;
  }
};
Object.defineProperty(requestPrototype, "signal", { get() {
  return this[getAbortController]().signal;
} });
[
  "cache",
  "credentials",
  "destination",
  "integrity",
  "mode",
  "redirect",
  "referrer",
  "referrerPolicy",
  "keepalive"
].forEach((k) => {
  Object.defineProperty(requestPrototype, k, { get() {
    return this[getRequestCache]()[k];
  } });
});
["clone", "formData"].forEach((k) => {
  Object.defineProperty(requestPrototype, k, { value: function() {
    if (this[bodyConsumedDirectlyKey]) {
      if (k === "clone") throw newBodyUnusableError();
      return rejectBodyUnusable();
    }
    return this[getRequestCache]()[k]();
  } });
});
Object.defineProperty(requestPrototype, "text", { value: function() {
  return readBodyWithFastPath(this, "text", (buf) => textDecoder.decode(buf));
} });
Object.defineProperty(requestPrototype, "arrayBuffer", { value: function() {
  return readBodyWithFastPath(this, "arrayBuffer", (buf) => toArrayBuffer(buf));
} });
Object.defineProperty(requestPrototype, "blob", { value: function() {
  return readBodyWithFastPath(this, "blob", (buf, request) => {
    const type = contentType(request);
    const init = type ? { headers: { "content-type": type } } : void 0;
    return new Response(buf, init).blob();
  });
} });
Object.defineProperty(requestPrototype, "json", { value: function() {
  if (this[bodyConsumedDirectlyKey]) return rejectBodyUnusable();
  return this.text().then(JSON.parse);
} });
Object.defineProperty(requestPrototype, /* @__PURE__ */ Symbol.for("nodejs.util.inspect.custom"), { value: function(depth, options, inspectFn) {
  return `Request (lightweight) ${inspectFn({
    method: this.method,
    url: this.url,
    headers: this.headers,
    nativeRequest: this[requestCache]
  }, {
    ...options,
    depth: depth == null ? null : depth - 1
  })}`;
} });
Object.setPrototypeOf(requestPrototype, Request$1.prototype);
var newRequest = (incoming, defaultHostname) => {
  const req = Object.create(requestPrototype);
  req[incomingKey] = incoming;
  req[methodKey] = normalizeIncomingMethod(incoming.method);
  const incomingUrl = incoming.url || "";
  if (incomingUrl[0] !== "/" && (incomingUrl.startsWith("http://") || incomingUrl.startsWith("https://"))) {
    if (incoming instanceof Http2ServerRequest) throw new RequestError("Absolute URL for :path is not allowed in HTTP/2");
    try {
      req[urlKey] = new URL(incomingUrl).href;
    } catch (e) {
      throw new RequestError("Invalid absolute URL", { cause: e });
    }
    return req;
  }
  const host2 = (incoming instanceof Http2ServerRequest ? incoming.authority : incoming.headers.host) || defaultHostname;
  if (!host2) throw new RequestError("Missing host header");
  let scheme;
  if (incoming instanceof Http2ServerRequest) {
    scheme = incoming.scheme;
    if (!(scheme === "http" || scheme === "https")) throw new RequestError("Unsupported scheme");
  } else scheme = incoming.socket && incoming.socket.encrypted ? "https" : "http";
  try {
    req[urlKey] = buildUrl(scheme, host2, incomingUrl);
  } catch (e) {
    if (e instanceof RequestError) throw e;
    else throw new RequestError("Invalid URL", { cause: e });
  }
  return req;
};
var defaultContentType = "text/plain; charset=UTF-8";
var responseCache = /* @__PURE__ */ Symbol("responseCache");
var getResponseCache = /* @__PURE__ */ Symbol("getResponseCache");
var cacheKey = /* @__PURE__ */ Symbol("cache");
var GlobalResponse = global.Response;
var Response$1 = class Response$12 {
  #body;
  #init;
  [getResponseCache]() {
    const cache = this[cacheKey];
    const liveHeaders = cache && cache[2] instanceof Headers ? cache[2] : void 0;
    delete this[cacheKey];
    return this[responseCache] ||= new GlobalResponse(this.#body, liveHeaders ? {
      status: this.#init?.status,
      statusText: this.#init?.statusText,
      headers: liveHeaders
    } : this.#init);
  }
  constructor(body, init) {
    let headers;
    this.#body = body;
    if (init instanceof GlobalResponse) {
      const cachedGlobalResponse = init[responseCache];
      if (cachedGlobalResponse) {
        this.#init = cachedGlobalResponse;
        this[getResponseCache]();
        return;
      }
      this.#init = init instanceof Response$12 ? init.#init : init;
      headers = new Headers(init.headers);
    } else this.#init = init;
    if (body == null || typeof body === "string" || typeof body?.getReader !== "undefined" || body instanceof Blob || body instanceof Uint8Array) this[cacheKey] = [
      init?.status || 200,
      body ?? null,
      headers || init?.headers
    ];
  }
  get headers() {
    const cache = this[cacheKey];
    if (cache) {
      if (!(cache[2] instanceof Headers)) cache[2] = new Headers(cache[2] || (cache[1] === null ? void 0 : { "content-type": defaultContentType }));
      return cache[2];
    }
    return this[getResponseCache]().headers;
  }
  get status() {
    return this[cacheKey]?.[0] ?? this[getResponseCache]().status;
  }
  get ok() {
    const status = this.status;
    return status >= 200 && status < 300;
  }
};
[
  "body",
  "bodyUsed",
  "redirected",
  "statusText",
  "trailers",
  "type",
  "url"
].forEach((k) => {
  Object.defineProperty(Response$1.prototype, k, { get() {
    return this[getResponseCache]()[k];
  } });
});
[
  "arrayBuffer",
  "blob",
  "clone",
  "formData",
  "json",
  "text"
].forEach((k) => {
  Object.defineProperty(Response$1.prototype, k, { value: function() {
    return this[getResponseCache]()[k]();
  } });
});
Object.defineProperty(Response$1.prototype, /* @__PURE__ */ Symbol.for("nodejs.util.inspect.custom"), { value: function(depth, options, inspectFn) {
  return `Response (lightweight) ${inspectFn({
    status: this.status,
    headers: this.headers,
    ok: this.ok,
    nativeResponse: this[responseCache]
  }, {
    ...options,
    depth: depth == null ? null : depth - 1
  })}`;
} });
Object.setPrototypeOf(Response$1, GlobalResponse);
Object.setPrototypeOf(Response$1.prototype, GlobalResponse.prototype);
var validRedirectUrl = /^https?:\/\/[!#-;=?-[\]_a-z~A-Z]+$/;
var parseRedirectUrl = (url2) => {
  if (url2 instanceof URL) return url2.href;
  if (validRedirectUrl.test(url2)) return url2;
  return new URL(url2).href;
};
var validRedirectStatuses = /* @__PURE__ */ new Set([
  301,
  302,
  303,
  307,
  308
]);
Object.defineProperty(Response$1, "redirect", {
  value: function redirect(url2, status = 302) {
    if (!validRedirectStatuses.has(status)) throw new RangeError("Invalid status code");
    return new Response$1(null, {
      status,
      headers: { location: parseRedirectUrl(url2) }
    });
  },
  writable: true,
  configurable: true
});
Object.defineProperty(Response$1, "json", {
  value: function json(data, init) {
    const body = JSON.stringify(data);
    if (body === void 0) throw new TypeError("The data is not JSON serializable");
    const initHeaders = init?.headers;
    let headers;
    if (initHeaders) {
      headers = new Headers(initHeaders);
      if (!headers.has("content-type")) headers.set("content-type", "application/json");
    } else headers = { "content-type": "application/json" };
    return new Response$1(body, {
      status: init?.status ?? 200,
      statusText: init?.statusText,
      headers
    });
  },
  writable: true,
  configurable: true
});
async function readWithoutBlocking(readPromise) {
  return Promise.race([readPromise, Promise.resolve().then(() => Promise.resolve(void 0))]);
}
function writeFromReadableStreamDefaultReader(reader, writable, currentReadPromise) {
  const cancel = (error) => {
    reader.cancel(error).catch(() => {
    });
  };
  writable.on("close", cancel);
  writable.on("error", cancel);
  (currentReadPromise ?? reader.read()).then(flow, handleStreamError);
  return reader.closed.finally(() => {
    writable.off("close", cancel);
    writable.off("error", cancel);
  });
  function handleStreamError(error) {
    if (error) writable.destroy(error);
  }
  function onDrain() {
    reader.read().then(flow, handleStreamError);
  }
  function flow({ done, value }) {
    try {
      if (done) writable.end();
      else if (!writable.write(value)) writable.once("drain", onDrain);
      else return reader.read().then(flow, handleStreamError);
    } catch (e) {
      handleStreamError(e);
    }
  }
}
function writeFromReadableStream(stream, writable) {
  if (stream.locked) throw new TypeError("ReadableStream is locked.");
  else if (writable.destroyed) return;
  return writeFromReadableStreamDefaultReader(stream.getReader(), writable);
}
var buildOutgoingHttpHeaders = (headers, defaultContentType2) => {
  const res = {};
  if (!(headers instanceof Headers)) headers = new Headers(headers ?? void 0);
  if (headers.has("set-cookie")) {
    const cookies = [];
    for (const [k, v] of headers) if (k === "set-cookie") cookies.push(v);
    else res[k] = v;
    if (cookies.length > 0) res["set-cookie"] = cookies;
  } else for (const [k, v] of headers) res[k] = v;
  if (defaultContentType2) res["content-type"] ??= defaultContentType2;
  return res;
};
var outgoingEnded = /* @__PURE__ */ Symbol("outgoingEnded");
var incomingDraining = /* @__PURE__ */ Symbol("incomingDraining");
var DRAIN_TIMEOUT_MS = 500;
var MAX_DRAIN_BYTES = 64 * 1024 * 1024;
var drainIncoming = (incoming) => {
  const incomingWithDrainState = incoming;
  if (incoming.destroyed || incomingWithDrainState[incomingDraining]) return;
  incomingWithDrainState[incomingDraining] = true;
  if (incoming instanceof Http2ServerRequest) {
    try {
      incoming.stream?.close?.(constants.NGHTTP2_NO_ERROR);
    } catch {
    }
    return;
  }
  let bytesRead = 0;
  const cleanup = () => {
    clearTimeout(timer);
    incoming.off("data", onData);
    incoming.off("end", cleanup);
    incoming.off("error", cleanup);
  };
  const forceClose = () => {
    cleanup();
    const socket = incoming.socket;
    if (socket && !socket.destroyed) {
      if (typeof socket.destroySoon === "function") socket.destroySoon();
      else if (typeof socket.destroy === "function") socket.destroy();
    }
  };
  const timer = setTimeout(forceClose, DRAIN_TIMEOUT_MS);
  timer.unref?.();
  const onData = (chunk) => {
    bytesRead += chunk.length;
    if (bytesRead > MAX_DRAIN_BYTES) forceClose();
  };
  incoming.on("data", onData);
  incoming.on("end", cleanup);
  incoming.on("error", cleanup);
  incoming.resume();
};
var makeCloseHandler = (req, incoming, outgoing, needsBodyCleanup) => () => {
  if (incoming.errored) {
    recordBodyBufferedBeforeDisconnect(incoming);
    req[abortRequest](incoming.errored.toString());
  } else if (!outgoing.writableFinished) {
    recordBodyBufferedBeforeDisconnect(incoming);
    req[abortRequest]("Client connection prematurely closed.");
  }
  if (needsBodyCleanup && !incoming.readableEnded) setTimeout(() => {
    if (!incoming.readableEnded) setTimeout(() => {
      drainIncoming(incoming);
    });
  });
};
var isImmediateCacheableResponse = (res) => {
  if (!(cacheKey in res)) return false;
  const body = res[cacheKey][1];
  return body === null || typeof body === "string" || body instanceof Uint8Array;
};
var handleRequestError = () => new Response(null, { status: 400 });
var handleFetchError = (e) => new Response(null, { status: e instanceof Error && (e.name === "TimeoutError" || e.constructor.name === "TimeoutError") ? 504 : 500 });
var handleResponseError = (e, outgoing) => {
  const err = e instanceof Error ? e : new Error("unknown error", { cause: e });
  if (err.code === "ERR_STREAM_PREMATURE_CLOSE") console.info("The user aborted a request.");
  else {
    console.error(e);
    if (!outgoing.headersSent) outgoing.writeHead(500, { "Content-Type": "text/plain" });
    outgoing.end(`Error: ${err.message}`);
    outgoing.destroy(err);
  }
};
var flushHeaders = (outgoing) => {
  if ("flushHeaders" in outgoing && outgoing.writable) outgoing.flushHeaders();
};
var responseViaCache = async (res, outgoing) => {
  let [status, body, header] = res[cacheKey];
  if (!header) {
    if (body === null) {
      outgoing.writeHead(status);
      outgoing.end();
    } else if (typeof body === "string") {
      outgoing.writeHead(status, {
        "Content-Type": defaultContentType,
        "Content-Length": Buffer.byteLength(body)
      });
      outgoing.end(body);
    } else if (body instanceof Uint8Array) {
      outgoing.writeHead(status, {
        "Content-Type": defaultContentType,
        "Content-Length": body.byteLength
      });
      outgoing.end(body);
    } else if (body instanceof Blob) {
      outgoing.writeHead(status, {
        "Content-Type": defaultContentType,
        "Content-Length": body.size
      });
      outgoing.end(new Uint8Array(await body.arrayBuffer()));
    } else {
      outgoing.writeHead(status, { "Content-Type": defaultContentType });
      flushHeaders(outgoing);
      await writeFromReadableStream(body, outgoing)?.catch((e) => handleResponseError(e, outgoing));
    }
    outgoing[outgoingEnded]?.();
    return;
  }
  let hasContentLength = false;
  if (header instanceof Headers) {
    hasContentLength = header.has("content-length");
    header = buildOutgoingHttpHeaders(header, body === null ? void 0 : defaultContentType);
  } else if (Array.isArray(header)) {
    const headerObj = new Headers(header);
    hasContentLength = headerObj.has("content-length");
    header = buildOutgoingHttpHeaders(headerObj, body === null ? void 0 : defaultContentType);
  } else for (const key in header) if (key.length === 14 && key.toLowerCase() === "content-length") {
    hasContentLength = true;
    break;
  }
  if (!hasContentLength) {
    if (typeof body === "string") header["Content-Length"] = Buffer.byteLength(body);
    else if (body instanceof Uint8Array) header["Content-Length"] = body.byteLength;
    else if (body instanceof Blob) header["Content-Length"] = body.size;
  }
  outgoing.writeHead(status, header);
  if (body == null) outgoing.end();
  else if (typeof body === "string" || body instanceof Uint8Array) outgoing.end(body);
  else if (body instanceof Blob) outgoing.end(new Uint8Array(await body.arrayBuffer()));
  else {
    flushHeaders(outgoing);
    await writeFromReadableStream(body, outgoing)?.catch((e) => handleResponseError(e, outgoing));
  }
  outgoing[outgoingEnded]?.();
};
var isPromise = (res) => typeof res.then === "function";
var responseViaResponseObject = async (res, outgoing, options = {}) => {
  if (isPromise(res)) if (options.errorHandler) try {
    res = await res;
  } catch (err) {
    const errRes = await options.errorHandler(err);
    if (!errRes) return;
    res = errRes;
  }
  else res = await res.catch(handleFetchError);
  if (cacheKey in res) return responseViaCache(res, outgoing);
  const resHeaderRecord = buildOutgoingHttpHeaders(res.headers, res.body === null ? void 0 : defaultContentType);
  if (res.body) {
    const reader = res.body.getReader();
    const values = [];
    let done = false;
    let currentReadPromise = void 0;
    if (resHeaderRecord["transfer-encoding"] !== "chunked") {
      let maxReadCount = 2;
      for (let i = 0; i < maxReadCount; i++) {
        currentReadPromise ||= reader.read();
        const chunk = await readWithoutBlocking(currentReadPromise).catch((e) => {
          console.error(e);
          done = true;
        });
        if (!chunk) {
          if (i === 1) {
            await new Promise((resolve3) => setTimeout(resolve3));
            maxReadCount = 3;
            continue;
          }
          break;
        }
        currentReadPromise = void 0;
        if (chunk.value) values.push(chunk.value);
        if (chunk.done) {
          done = true;
          break;
        }
      }
      if (done && !("content-length" in resHeaderRecord)) resHeaderRecord["content-length"] = values.reduce((acc, value) => acc + value.length, 0);
    }
    outgoing.writeHead(res.status, resHeaderRecord);
    values.forEach((value) => {
      outgoing.write(value);
    });
    if (done) outgoing.end();
    else {
      if (values.length === 0) flushHeaders(outgoing);
      await writeFromReadableStreamDefaultReader(reader, outgoing, currentReadPromise);
    }
  } else if (resHeaderRecord[X_ALREADY_SENT]) {
  } else {
    outgoing.writeHead(res.status, resHeaderRecord);
    outgoing.end();
  }
  outgoing[outgoingEnded]?.();
};
var getRequestListener = (fetchCallback, options = {}) => {
  const autoCleanupIncoming = options.autoCleanupIncoming ?? true;
  if (options.overrideGlobalObjects !== false && global.Request !== Request$1) {
    Object.defineProperty(global, "Request", { value: Request$1 });
    Object.defineProperty(global, "Response", { value: Response$1 });
  }
  return async (incoming, outgoing) => {
    let res, req;
    let needsBodyCleanup = false;
    let closeHandlerAttached = false;
    const ensureCloseHandler = () => {
      if (!req || closeHandlerAttached) return;
      closeHandlerAttached = true;
      outgoing.on("close", makeCloseHandler(req, incoming, outgoing, needsBodyCleanup));
    };
    try {
      req = newRequest(incoming, options.hostname);
      needsBodyCleanup = autoCleanupIncoming && !(incoming.method === "GET" || incoming.method === "HEAD");
      if (needsBodyCleanup) {
        incoming[wrapBodyStream] = true;
        if (incoming instanceof Http2ServerRequest) outgoing[outgoingEnded] = () => {
          if (!incoming.readableEnded) setTimeout(() => {
            if (!incoming.readableEnded) setTimeout(() => {
              incoming.destroy();
              outgoing.destroy();
            });
          });
        };
      }
      res = fetchCallback(req, {
        incoming,
        outgoing
      });
      if (!isPromise(res) && isImmediateCacheableResponse(res)) {
        if (needsBodyCleanup && !incoming.readableEnded) outgoing.once("finish", () => {
          if (!incoming.readableEnded) drainIncoming(incoming);
        });
        return responseViaCache(res, outgoing);
      }
      ensureCloseHandler();
    } catch (e) {
      if (!res) if (options.errorHandler) {
        ensureCloseHandler();
        res = await options.errorHandler(req ? e : toRequestError(e));
        if (!res) return;
      } else if (!req) res = handleRequestError();
      else res = handleFetchError(e);
      else return handleResponseError(e, outgoing);
    }
    try {
      return await responseViaResponseObject(res, outgoing, options);
    } catch (e) {
      return handleResponseError(e, outgoing);
    }
  };
};
var CloseEvent = globalThis.CloseEvent ?? class extends Event {
  #eventInitDict;
  constructor(type, eventInitDict = {}) {
    super(type, eventInitDict);
    this.#eventInitDict = eventInitDict;
  }
  get wasClean() {
    return this.#eventInitDict.wasClean ?? false;
  }
  get code() {
    return this.#eventInitDict.code ?? 0;
  }
  get reason() {
    return this.#eventInitDict.reason ?? "";
  }
};
var ErrorEvent = globalThis.ErrorEvent ?? class extends Event {
  #eventInitDict;
  constructor(type, eventInitDict = {}) {
    super(type, eventInitDict);
    this.#eventInitDict = eventInitDict;
  }
  get message() {
    return this.#eventInitDict.message ?? "";
  }
  get filename() {
    return this.#eventInitDict.filename ?? "";
  }
  get lineno() {
    return this.#eventInitDict.lineno ?? 0;
  }
  get colno() {
    return this.#eventInitDict.colno ?? 0;
  }
  get error() {
    return this.#eventInitDict.error ?? null;
  }
};
var generateConnectionSymbol = () => /* @__PURE__ */ Symbol("connection");
var CONNECTION_SYMBOL_KEY = /* @__PURE__ */ Symbol("CONNECTION_SYMBOL_KEY");
var WAIT_FOR_WEBSOCKET_SYMBOL = /* @__PURE__ */ Symbol("WAIT_FOR_WEBSOCKET_SYMBOL");
var responseHeadersToSkip = /* @__PURE__ */ new Set([
  "connection",
  "content-length",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "sec-websocket-accept",
  "sec-websocket-extensions",
  "sec-websocket-protocol"
]);
var appendResponseHeaders = (headers, responseHeaders) => {
  if (!responseHeaders) return;
  responseHeaders.forEach((value, key) => {
    if (responseHeadersToSkip.has(key.toLowerCase())) return;
    headers.push(`${key}: ${value}`);
  });
};
var rejectUpgradeRequest = (socket, status, responseHeaders) => {
  const responseLines = ["Connection: close", "Content-Length: 0"];
  appendResponseHeaders(responseLines, responseHeaders);
  socket.end(`HTTP/1.1 ${status.toString()} ${STATUS_CODES[status] ?? ""}\r
${responseLines.join("\r\n")}\r
\r
`);
};
var createUpgradeRequest = (request) => {
  const protocol = request.socket.encrypted ? "https" : "http";
  const url2 = new URL(request.url ?? "/", `${protocol}://${request.headers.host ?? "localhost"}`);
  const headers = new Headers();
  for (const key in request.headers) {
    const value = request.headers[key];
    if (!value) continue;
    headers.append(key, Array.isArray(value) ? value[0] : value);
  }
  return new Request(url2, { headers });
};
var setupWebSocket = (options) => {
  const { server: server2, fetchCallback, wss } = options;
  const waiterMap = /* @__PURE__ */ new Map();
  wss.on("connection", (ws, request) => {
    const waiter = waiterMap.get(request);
    if (waiter) {
      waiter.resolve(ws);
      waiterMap.delete(request);
    }
  });
  const rejectWaiter = (request) => {
    const waiter = waiterMap.get(request);
    if (waiter) {
      waiterMap.delete(request);
      waiter.reject(/* @__PURE__ */ new Error("WebSocket handshake aborted"));
    }
  };
  const waitForWebSocket = (request, connectionSymbol) => {
    return new Promise((resolve3, reject) => {
      waiterMap.set(request, {
        resolve: resolve3,
        reject,
        connectionSymbol
      });
    });
  };
  server2.on("upgrade", async (request, socket, head) => {
    if (request.headers.upgrade?.toLowerCase() !== "websocket") return;
    const env = {
      incoming: request,
      outgoing: void 0,
      wss,
      [WAIT_FOR_WEBSOCKET_SYMBOL]: waitForWebSocket
    };
    let status = 400;
    let responseHeaders;
    try {
      const response = await fetchCallback(createUpgradeRequest(request), env);
      if (response instanceof Response) {
        status = response.status;
        responseHeaders = response.headers;
      }
    } catch {
      if (server2.listenerCount("upgrade") === 1) rejectUpgradeRequest(socket, 500);
      return;
    }
    const waiter = waiterMap.get(request);
    if (!waiter || waiter.connectionSymbol !== env[CONNECTION_SYMBOL_KEY]) {
      rejectWaiter(request);
      if (server2.listenerCount("upgrade") === 1) rejectUpgradeRequest(socket, status, responseHeaders);
      return;
    }
    const addResponseHeaders = (headers) => {
      appendResponseHeaders(headers, responseHeaders);
    };
    const reclaimWaiterOnClose = () => rejectWaiter(request);
    socket.once("close", reclaimWaiterOnClose);
    wss.on("headers", addResponseHeaders);
    try {
      wss.handleUpgrade(request, socket, head, (ws) => {
        socket.off("close", reclaimWaiterOnClose);
        wss.emit("connection", ws, request);
      });
    } finally {
      wss.off("headers", addResponseHeaders);
    }
  });
  server2.on("close", () => {
    wss.close();
  });
};
var upgradeWebSocket = defineWebSocketHelper(async (c, events, options) => {
  if (c.req.header("upgrade")?.toLowerCase() !== "websocket") return;
  const env = c.env;
  const waitForWebSocket = env[WAIT_FOR_WEBSOCKET_SYMBOL];
  if (!waitForWebSocket || !env.incoming) return new Response(null, { status: 500 });
  const connectionSymbol = generateConnectionSymbol();
  env[CONNECTION_SYMBOL_KEY] = connectionSymbol;
  (async () => {
    let ws;
    try {
      ws = await waitForWebSocket(env.incoming, connectionSymbol);
    } catch {
      return;
    }
    const messagesReceivedInStarting = [];
    const bufferMessage = (data, isBinary) => {
      messagesReceivedInStarting.push([data, isBinary]);
    };
    ws.on("message", bufferMessage);
    const ctx = {
      binaryType: "arraybuffer",
      close(code, reason) {
        ws.close(code, reason);
      },
      protocol: ws.protocol,
      raw: ws,
      get readyState() {
        return ws.readyState;
      },
      send(source, opts) {
        ws.send(source, { compress: opts?.compress });
      },
      url: new URL(c.req.url)
    };
    try {
      events?.onOpen?.(new Event("open"), ctx);
    } catch (e) {
      (options?.onError ?? console.error)(e);
    }
    const handleMessage = (data, isBinary) => {
      const datas = Array.isArray(data) ? data : [data];
      for (const data2 of datas) try {
        events?.onMessage?.(new MessageEvent("message", { data: isBinary ? data2 instanceof ArrayBuffer ? data2 : data2.buffer.slice(data2.byteOffset, data2.byteOffset + data2.byteLength) : typeof data2 === "string" ? data2 : Buffer.from(data2).toString("utf-8") }), ctx);
      } catch (e) {
        (options?.onError ?? console.error)(e);
      }
    };
    ws.off("message", bufferMessage);
    for (const message of messagesReceivedInStarting) handleMessage(...message);
    ws.on("message", (data, isBinary) => {
      handleMessage(data, isBinary);
    });
    ws.on("close", (code, reason) => {
      try {
        events?.onClose?.(new CloseEvent("close", {
          code,
          reason: reason.toString()
        }), ctx);
      } catch (e) {
        (options?.onError ?? console.error)(e);
      }
    });
    ws.on("error", (error) => {
      try {
        events?.onError?.(new ErrorEvent("error", { error }), ctx);
      } catch (e) {
        (options?.onError ?? console.error)(e);
      }
    });
  })();
  return new Response();
});
var createAdaptorServer = (options) => {
  const fetchCallback = options.fetch;
  const requestListener = getRequestListener(fetchCallback, {
    hostname: options.hostname,
    overrideGlobalObjects: options.overrideGlobalObjects,
    autoCleanupIncoming: options.autoCleanupIncoming
  });
  const server2 = (options.createServer || createServer)(options.serverOptions || {}, requestListener);
  if (options.websocket && options.websocket.server) {
    if (options.websocket.server.options.noServer !== true) throw new Error("WebSocket server must be created with { noServer: true } option");
    setupWebSocket({
      server: server2,
      fetchCallback,
      wss: options.websocket.server
    });
  }
  return server2;
};
var serve = (options, listeningListener) => {
  const server2 = createAdaptorServer(options);
  server2.listen(options?.port ?? 3e3, options.hostname, () => {
    const serverInfo = server2.address();
    listeningListener && listeningListener(serverInfo);
  });
  return server2;
};

// node_modules/@hono/node-server/dist/utils/stream.mjs
import { Readable as Readable2 } from "node:stream";
import { versions } from "node:process";
var pr54206Applied = () => {
  const [major, minor] = versions.node.split(".").map((component) => parseInt(component));
  return major >= 23 || major === 22 && minor >= 7 || major === 20 && minor >= 18;
};
var useReadableToWeb = pr54206Applied();
var createStreamBody = (stream, useNativeReadableToWeb = useReadableToWeb) => {
  if (useNativeReadableToWeb) return Readable2.toWeb(stream);
  let controller;
  let settled = false;
  const cleanup = () => {
    stream.off("data", onData);
    stream.off("error", onError);
    stream.off("end", onTerminate);
    stream.off("close", onTerminate);
  };
  const settle = (callback) => {
    if (settled) return;
    settled = true;
    cleanup();
    callback?.();
  };
  const onData = (chunk) => {
    if (settled || !controller) return;
    controller.enqueue(chunk);
    if ((controller.desiredSize ?? 0) <= 0) stream.pause();
  };
  const onError = (error) => {
    settle(() => {
      controller?.error(error);
    });
  };
  const onTerminate = () => {
    settle(() => {
      controller?.close();
    });
  };
  return new ReadableStream({
    start(streamController) {
      controller = streamController;
      stream.on("data", onData);
      stream.on("error", onError);
      stream.on("end", onTerminate);
      stream.on("close", onTerminate);
      stream.pause();
    },
    pull() {
      if (!settled) stream.resume();
    },
    cancel() {
      settle();
      const ignoreError = () => {
      };
      stream.on("error", ignoreError);
      stream.once("close", () => stream.off("error", ignoreError));
      stream.destroy();
    }
  });
};

// node_modules/hono/dist/utils/mime.js
var getMimeType = (filename, mimes = baseMimes) => {
  const regexp = /\.([a-zA-Z0-9]+?)$/;
  const match2 = filename.match(regexp);
  if (!match2) {
    return;
  }
  return mimes[match2[1].toLowerCase()];
};
var _baseMimes = {
  aac: "audio/aac",
  avi: "video/x-msvideo",
  avif: "image/avif",
  av1: "video/av1",
  bin: "application/octet-stream",
  bmp: "image/bmp",
  css: "text/css; charset=utf-8",
  csv: "text/csv; charset=utf-8",
  eot: "application/vnd.ms-fontobject",
  epub: "application/epub+zip",
  gif: "image/gif",
  gz: "application/gzip",
  htm: "text/html; charset=utf-8",
  html: "text/html; charset=utf-8",
  ico: "image/x-icon",
  ics: "text/calendar; charset=utf-8",
  jpeg: "image/jpeg",
  jpg: "image/jpeg",
  js: "text/javascript; charset=utf-8",
  json: "application/json",
  jsonld: "application/ld+json",
  map: "application/json",
  mid: "audio/x-midi",
  midi: "audio/x-midi",
  mjs: "text/javascript; charset=utf-8",
  mp3: "audio/mpeg",
  mp4: "video/mp4",
  mpeg: "video/mpeg",
  oga: "audio/ogg",
  ogv: "video/ogg",
  ogx: "application/ogg",
  opus: "audio/opus",
  otf: "font/otf",
  pdf: "application/pdf",
  png: "image/png",
  rtf: "application/rtf",
  svg: "image/svg+xml; charset=utf-8",
  tif: "image/tiff",
  tiff: "image/tiff",
  ts: "video/mp2t",
  ttf: "font/ttf",
  txt: "text/plain; charset=utf-8",
  wasm: "application/wasm",
  webm: "video/webm",
  weba: "audio/webm",
  webmanifest: "application/manifest+json",
  webp: "image/webp",
  woff: "font/woff",
  woff2: "font/woff2",
  xhtml: "application/xhtml+xml; charset=utf-8",
  xml: "application/xml; charset=utf-8",
  zip: "application/zip",
  "3gp": "video/3gpp",
  "3g2": "video/3gpp2",
  gltf: "model/gltf+json",
  glb: "model/gltf-binary"
};
var baseMimes = _baseMimes;

// node_modules/@hono/node-server/dist/serve-static.mjs
import { createReadStream, existsSync, statSync } from "node:fs";
import { join } from "node:path";
var COMPRESSIBLE_CONTENT_TYPE_REGEX = /^\s*(?:text\/[^;\s]+|application\/(?:javascript|json|xml|xml-dtd|ecmascript|dart|postscript|rtf|tar|toml|vnd\.dart|vnd\.ms-fontobject|vnd\.ms-opentype|wasm|x-httpd-php|x-javascript|x-ns-proxy-autoconfig|x-sh|x-tar|x-virtualbox-hdd|x-virtualbox-ova|x-virtualbox-ovf|x-virtualbox-vbox|x-virtualbox-vdi|x-virtualbox-vhd|x-virtualbox-vmdk|x-www-form-urlencoded)|font\/(?:otf|ttf)|image\/(?:bmp|vnd\.adobe\.photoshop|vnd\.microsoft\.icon|vnd\.ms-dds|x-icon|x-ms-bmp)|message\/rfc822|model\/gltf-binary|x-shader\/x-fragment|x-shader\/x-vertex|[^;\s]+?\+(?:json|text|xml|yaml))(?:[;\s]|$)/i;
var ENCODINGS = {
  br: ".br",
  zstd: ".zst",
  gzip: ".gz"
};
var ENCODINGS_ORDERED_KEYS = Object.keys(ENCODINGS);
var getStats = (path) => {
  let stats;
  try {
    stats = statSync(path);
  } catch {
  }
  return stats;
};
var BYTE_RANGE_PATTERN = /^(?:bytes=)?(?!-$)(\d*)-(\d*)$/;
var parseByteRange = (range) => {
  const match2 = range.match(BYTE_RANGE_PATTERN);
  if (!match2) return;
  const [, start, end] = match2;
  if (start === "") return {
    type: "suffix",
    length: Number(end)
  };
  if (end === "") return {
    type: "open-ended",
    start: Number(start)
  };
  return {
    type: "bounded",
    start: Number(start),
    end: Number(end)
  };
};
var resolveByteRange = (spec, size) => {
  if (size === 0) return;
  if (spec.type === "suffix") {
    if (spec.length === 0) return;
    return {
      start: Math.max(size - spec.length, 0),
      end: size - 1
    };
  }
  const end = spec.type === "bounded" ? Math.min(spec.end, size - 1) : size - 1;
  if (spec.start >= size || spec.start > end) return;
  return {
    start: spec.start,
    end
  };
};
var tryDecode2 = (str2, decoder) => {
  try {
    return decoder(str2);
  } catch {
    return str2.replace(/(?:%[0-9A-Fa-f]{2})+/g, (match2) => {
      try {
        return decoder(match2);
      } catch {
        return match2;
      }
    });
  }
};
var tryDecodeURI2 = (str2) => tryDecode2(str2, decodeURI);
var serveStatic = (options = { root: "" }) => {
  const root = options.root || "";
  const optionPath = options.path;
  if (root !== "" && !existsSync(root)) console.error(`serveStatic: root path '${root}' is not found, are you sure it's correct?`);
  return async (c, next) => {
    if (c.finalized) return next();
    let filename;
    if (optionPath) filename = optionPath;
    else try {
      filename = tryDecodeURI2(c.req.path);
      if (/(?:^|[\/\\])\.{1,2}(?:$|[\/\\])|[\/\\]{2,}|\\/.test(filename)) throw new Error();
    } catch {
      await options.onNotFound?.(c.req.path, c);
      return next();
    }
    let path = join(root, !optionPath && options.rewriteRequestPath ? options.rewriteRequestPath(filename, c) : filename);
    let stats = getStats(path);
    if (stats && stats.isDirectory()) {
      const indexFile = options.index ?? "index.html";
      path = join(path, indexFile);
      stats = getStats(path);
    }
    if (!stats) {
      await options.onNotFound?.(path, c);
      return next();
    }
    const mimeType = getMimeType(path);
    c.header("Content-Type", mimeType || "application/octet-stream");
    if (options.precompressed && (!mimeType || mimeType === "application/octet-stream" || COMPRESSIBLE_CONTENT_TYPE_REGEX.test(mimeType))) {
      const acceptEncodingSet = new Set(c.req.header("Accept-Encoding")?.split(",").map((encoding) => encoding.trim()));
      for (const encoding of ENCODINGS_ORDERED_KEYS) {
        if (!acceptEncodingSet.has(encoding)) continue;
        const precompressedStats = getStats(path + ENCODINGS[encoding]);
        if (precompressedStats) {
          c.header("Content-Encoding", encoding);
          c.header("Vary", "Accept-Encoding", { append: true });
          stats = precompressedStats;
          path = path + ENCODINGS[encoding];
          break;
        }
      }
    }
    let result;
    const size = stats.size;
    const range = c.req.header("range") || "";
    c.header("Last-Modified", stats.mtime.toUTCString());
    if (c.req.method == "HEAD" || c.req.method == "OPTIONS") {
      c.header("Content-Length", size.toString());
      c.status(200);
      result = c.body(null);
    } else if (!range) {
      c.header("Content-Length", size.toString());
      result = c.body(createStreamBody(createReadStream(path)), 200);
    } else {
      c.header("Accept-Ranges", "bytes");
      const resolvedRange = resolveByteRange(parseByteRange(range) ?? {
        type: "open-ended",
        start: 0
      }, size);
      if (!resolvedRange) {
        c.header("Content-Range", `bytes */${size}`);
        result = c.body(null, 416);
      } else {
        const { start, end } = resolvedRange;
        const chunkSize = end - start + 1;
        const stream = createReadStream(path, {
          start,
          end
        });
        c.header("Content-Length", chunkSize.toString());
        c.header("Content-Range", `bytes ${start}-${end}/${size}`);
        result = c.body(createStreamBody(stream), 206);
      }
    }
    await options.onFound?.(path, c);
    return result;
  };
};

// node_modules/hono/dist/utils/cookie.js
var validCookieNameRegEx = /^[\w!#$%&'*.^`|~+-]+$/;
var relaxedCookieNameRegEx = /^[!#-:<>-[\]-~]+$/;
var validCookieValueRegEx = /^[ !#-:<-[\]-~]*$/;
var trimCookieWhitespace = (value) => {
  let start = 0;
  let end = value.length;
  while (start < end) {
    const charCode = value.charCodeAt(start);
    if (charCode !== 32 && charCode !== 9) {
      break;
    }
    start++;
  }
  while (end > start) {
    const charCode = value.charCodeAt(end - 1);
    if (charCode !== 32 && charCode !== 9) {
      break;
    }
    end--;
  }
  return start === 0 && end === value.length ? value : value.slice(start, end);
};
var parse = (cookie, name) => {
  if (name && cookie.indexOf(name) === -1) {
    return {};
  }
  const pairs = cookie.split(";");
  const parsedCookie = /* @__PURE__ */ Object.create(null);
  for (const pairStr of pairs) {
    const valueStartPos = pairStr.indexOf("=");
    if (valueStartPos === -1) {
      continue;
    }
    const cookieName = trimCookieWhitespace(pairStr.substring(0, valueStartPos));
    if (name && name !== cookieName || !relaxedCookieNameRegEx.test(cookieName) || cookieName in parsedCookie) {
      continue;
    }
    let cookieValue = trimCookieWhitespace(pairStr.substring(valueStartPos + 1));
    if (cookieValue.startsWith('"') && cookieValue.endsWith('"')) {
      cookieValue = cookieValue.slice(1, -1);
    }
    if (validCookieValueRegEx.test(cookieValue)) {
      parsedCookie[cookieName] = tryDecodeURIComponent(cookieValue);
      if (name) {
        break;
      }
    }
  }
  return parsedCookie;
};
var _serialize = (name, value, opt = {}) => {
  if (!validCookieNameRegEx.test(name)) {
    throw new Error("Invalid cookie name");
  }
  let cookie = `${name}=${value}`;
  if (name.startsWith("__Secure-") && !opt.secure) {
    throw new Error("__Secure- Cookie must have Secure attributes");
  }
  if (name.startsWith("__Host-")) {
    if (!opt.secure) {
      throw new Error("__Host- Cookie must have Secure attributes");
    }
    if (opt.path !== "/") {
      throw new Error('__Host- Cookie must have Path attributes with "/"');
    }
    if (opt.domain) {
      throw new Error("__Host- Cookie must not have Domain attributes");
    }
  }
  for (const key of ["domain", "path", "sameSite", "priority"]) {
    if (opt[key] && /[;\r\n]/.test(opt[key])) {
      throw new Error(`${key} must not contain ";", "\\r", or "\\n"`);
    }
  }
  if (opt && typeof opt.maxAge === "number" && opt.maxAge >= 0) {
    if (opt.maxAge > 3456e4) {
      throw new Error(
        "Cookies Max-Age SHOULD NOT be greater than 400 days (34560000 seconds) in duration."
      );
    }
    cookie += `; Max-Age=${opt.maxAge | 0}`;
  }
  if (opt.domain && opt.prefix !== "host") {
    cookie += `; Domain=${opt.domain}`;
  }
  if (opt.path) {
    cookie += `; Path=${opt.path}`;
  }
  if (opt.expires) {
    if (opt.expires.getTime() - Date.now() > 3456e7) {
      throw new Error(
        "Cookies Expires SHOULD NOT be greater than 400 days (34560000 seconds) in the future."
      );
    }
    cookie += `; Expires=${opt.expires.toUTCString()}`;
  }
  if (opt.httpOnly) {
    cookie += "; HttpOnly";
  }
  if (opt.secure) {
    cookie += "; Secure";
  }
  if (opt.sameSite) {
    cookie += `; SameSite=${opt.sameSite.charAt(0).toUpperCase() + opt.sameSite.slice(1)}`;
  }
  if (opt.priority) {
    cookie += `; Priority=${opt.priority.charAt(0).toUpperCase() + opt.priority.slice(1)}`;
  }
  if (opt.partitioned) {
    if (!opt.secure) {
      throw new Error("Partitioned Cookie must have Secure attributes");
    }
    cookie += "; Partitioned";
  }
  return cookie;
};
var serialize = (name, value, opt) => {
  value = encodeURIComponent(value);
  return _serialize(name, value, opt);
};

// node_modules/hono/dist/helper/cookie/index.js
var getCookie = (c, key, prefix) => {
  const cookie = c.req.raw.headers.get("Cookie");
  if (typeof key === "string") {
    if (!cookie) {
      return void 0;
    }
    let finalKey = key;
    if (prefix === "secure") {
      finalKey = "__Secure-" + key;
    } else if (prefix === "host") {
      finalKey = "__Host-" + key;
    }
    const obj2 = parse(cookie, finalKey);
    return obj2[finalKey];
  }
  if (!cookie) {
    return {};
  }
  const obj = parse(cookie);
  return obj;
};
var generateCookie = (name, value, opt) => {
  let cookie;
  if (opt?.prefix === "secure") {
    cookie = serialize("__Secure-" + name, value, { path: "/", ...opt, secure: true });
  } else if (opt?.prefix === "host") {
    cookie = serialize("__Host-" + name, value, {
      ...opt,
      path: "/",
      secure: true,
      domain: void 0
    });
  } else {
    cookie = serialize(name, value, { path: "/", ...opt });
  }
  return cookie;
};
var setCookie = (c, name, value, opt) => {
  const cookie = generateCookie(name, value, opt);
  c.header("Set-Cookie", cookie, { append: true });
};
var deleteCookie = (c, name, opt) => {
  const deletedCookie = getCookie(c, name, opt?.prefix);
  setCookie(c, name, "", { ...opt, maxAge: 0 });
  return deletedCookie;
};

// node_modules/hono/dist/helper/factory/index.js
var createMiddleware = (middleware) => middleware;

// server/index.ts
import { existsSync as existsSync2, readFileSync, rmSync } from "node:fs";
import { readFile, writeFile } from "node:fs/promises";
import { randomBytes as randomBytes2 } from "node:crypto";
import { spawn } from "node:child_process";
import { networkInterfaces } from "node:os";
import { extname, resolve as resolve2 } from "node:path";
import { gunzipSync, gzipSync } from "node:zlib";

// server/db.ts
import { DatabaseSync } from "node:sqlite";
import { mkdirSync } from "node:fs";
import { randomBytes, scryptSync, timingSafeEqual } from "node:crypto";

// server/paths.ts
import { resolve } from "node:path";
var ROOT = resolve(import.meta.dirname, "..");
var DATA_DIR = resolve(ROOT, "data");
var UPLOADS_DIR = resolve(DATA_DIR, "uploads");
var DB_FILE = resolve(DATA_DIR, "uav.db");
var DIST_DIR = resolve(ROOT, "dist");

// server/seed-data.ts
var ttx = (rows) => "| \u041F\u0430\u0440\u0430\u043C\u0435\u0442\u0440 | \u0417\u043D\u0430\u0447\u0435\u043D\u0438\u0435 |\n|---|---|\n" + rows.map(([k, v]) => `| ${k} | ${v} |`).join("\n");
var todo = (params) => ttx(params.map((p) => [p, "\u2014"])) + "\n\n*\u0417\u043D\u0430\u0447\u0435\u043D\u0438\u044F \u0432\u043D\u043E\u0441\u0438\u0442 \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440 \u043F\u043E \u0434\u043E\u043A\u0443\u043C\u0435\u043D\u0442\u0430\u0446\u0438\u0438 \u0438\u0437\u0434\u0435\u043B\u0438\u044F.*";
var QUAD_SCHEMES = [
  {
    title: "\u041E\u0431\u0449\u0438\u0439 \u0432\u0438\u0434",
    model: "/models/quad.glb",
    image: "/schemes/quad.svg",
    size: [800, 520],
    parts: [
      {
        key: "frame",
        title: "\u0420\u0430\u043C\u0430",
        label: ["\u0420\u0430\u043C\u0430 \u2014 \u043D\u0435\u0441\u0443\u0449\u0430\u044F \u043A\u043E\u043D\u0441\u0442\u0440\u0443\u043A\u0446\u0438\u044F"],
        boxes: [[40, 70, 720, 56]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0422\u0438\u043F", "X-\u043E\u0431\u0440\u0430\u0437\u043D\u0430\u044F, 5\u2033"], ["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u0443\u0433\u043B\u0435\u043F\u043B\u0430\u0441\u0442\u0438\u043A: \u043B\u0443\u0447\u0438 5 \u043C\u043C, \u043F\u043B\u0430\u0441\u0442\u0438\u043D\u044B 2 \u043C\u043C"], ["\u0414\u0438\u0430\u0433\u043E\u043D\u0430\u043B\u044C (\u043C\u043E\u0442\u043E\u0440\u2013\u043C\u043E\u0442\u043E\u0440)", "225 \u043C\u043C"], ["\u041C\u0430\u0441\u0441\u0430", "120 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041D\u0435\u0441\u0443\u0449\u0430\u044F \u043A\u043E\u043D\u0441\u0442\u0440\u0443\u043A\u0446\u0438\u044F \u0430\u043F\u043F\u0430\u0440\u0430\u0442\u0430. \u041D\u0430 \u043B\u0443\u0447\u0430\u0445 \u0437\u0430\u043A\u0440\u0435\u043F\u043B\u0435\u043D\u044B \u043C\u043E\u0442\u043E\u0440\u044B, \u043C\u0435\u0436\u0434\u0443 \u043F\u043B\u0430\u0441\u0442\u0438\u043D\u0430\u043C\u0438 \u043D\u0430 \u0441\u0442\u043E\u0439\u043A\u0430\u0445 \u0440\u0430\u0437\u043C\u0435\u0449\u0430\u0435\u0442\u0441\u044F \u044D\u043B\u0435\u043A\u0442\u0440\u043E\u043D\u0438\u043A\u0430 (\xAB\u0441\u0442\u0435\u043A\xBB: ESC + \u043F\u043E\u043B\u0451\u0442\u043D\u044B\u0439 \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440), \u0441\u0432\u0435\u0440\u0445\u0443 \u2014 \u0430\u043A\u043A\u0443\u043C\u0443\u043B\u044F\u0442\u043E\u0440."]
        ]
      },
      {
        key: "battery",
        title: "\u0410\u043A\u043A\u0443\u043C\u0443\u043B\u044F\u0442\u043E\u0440 LiPo",
        label: ["\u0410\u043A\u043A\u0443\u043C\u0443\u043B\u044F\u0442\u043E\u0440", "LiPo 6S"],
        boxes: [[40, 170, 160, 70]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041A\u043E\u043D\u0444\u0438\u0433\u0443\u0440\u0430\u0446\u0438\u044F", "6S1P"], ["\u041D\u0430\u043F\u0440\u044F\u0436\u0435\u043D\u0438\u0435", "22,2 \u0412 \u043D\u043E\u043C\u0438\u043D\u0430\u043B / 25,2 \u0412 \u043F\u043E\u043B\u043D\u044B\u0439 \u0437\u0430\u0440\u044F\u0434"], ["\u0401\u043C\u043A\u043E\u0441\u0442\u044C", "1300 \u043C\u0410\xB7\u0447"], ["\u0422\u043E\u043A\u043E\u043E\u0442\u0434\u0430\u0447\u0430", "100C"], ["\u0420\u0430\u0437\u044A\u0451\u043C", "XT60"], ["\u041C\u0430\u0441\u0441\u0430", "210 \u0433"]])],
          ["\u042D\u043A\u0441\u043F\u043B\u0443\u0430\u0442\u0430\u0446\u0438\u044F", "- \u041D\u0435 \u0440\u0430\u0437\u0440\u044F\u0436\u0430\u0442\u044C \u043D\u0438\u0436\u0435 **3,5 \u0412** \u043D\u0430 \u0431\u0430\u043D\u043A\u0443 \u043F\u043E\u0434 \u043D\u0430\u0433\u0440\u0443\u0437\u043A\u043E\u0439.\n- \u0425\u0440\u0430\u043D\u0435\u043D\u0438\u0435 \u2014 \u043F\u0440\u0438 **3,8 \u0412** \u043D\u0430 \u0431\u0430\u043D\u043A\u0443.\n- \u0417\u0430\u0440\u044F\u0434 \u0442\u043E\u043B\u044C\u043A\u043E \u0447\u0435\u0440\u0435\u0437 \u0431\u0430\u043B\u0430\u043D\u0441\u0438\u0440\u043E\u0432\u043E\u0447\u043D\u044B\u0439 \u0440\u0430\u0437\u044A\u0451\u043C.\n- \u0412\u0437\u0434\u0443\u0432\u0448\u0438\u0439\u0441\u044F \u0430\u043A\u043A\u0443\u043C\u0443\u043B\u044F\u0442\u043E\u0440 \u043A \u044D\u043A\u0441\u043F\u043B\u0443\u0430\u0442\u0430\u0446\u0438\u0438 \u043D\u0435 \u0434\u043E\u043F\u0443\u0441\u043A\u0430\u0435\u0442\u0441\u044F."]
        ]
      },
      {
        key: "esc",
        title: "\u0420\u0435\u0433\u0443\u043B\u044F\u0442\u043E\u0440 \u043E\u0431\u043E\u0440\u043E\u0442\u043E\u0432 (ESC 4-\u0432-1)",
        label: ["\u0420\u0435\u0433\u0443\u043B\u044F\u0442\u043E\u0440", "ESC 4-\u0432-1"],
        boxes: [[260, 170, 160, 70]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041A\u0430\u043D\u0430\u043B\u044B", "4"], ["\u0422\u043E\u043A", "45 \u0410 \u0434\u043B\u0438\u0442\u0435\u043B\u044C\u043D\u043E \u043D\u0430 \u043A\u0430\u043D\u0430\u043B"], ["\u041F\u0438\u0442\u0430\u043D\u0438\u0435", "3\u20136S"], ["\u041F\u0440\u043E\u0442\u043E\u043A\u043E\u043B", "DShot600"], ["\u041F\u0440\u043E\u0448\u0438\u0432\u043A\u0430", "BLHeli_32 / AM32"], ["\u041C\u0430\u0441\u0441\u0430", "12 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041F\u0440\u0435\u043E\u0431\u0440\u0430\u0437\u0443\u0435\u0442 \u043F\u043E\u0441\u0442\u043E\u044F\u043D\u043D\u043E\u0435 \u043D\u0430\u043F\u0440\u044F\u0436\u0435\u043D\u0438\u0435 \u0430\u043A\u043A\u0443\u043C\u0443\u043B\u044F\u0442\u043E\u0440\u0430 \u0432 \u0442\u0440\u0451\u0445\u0444\u0430\u0437\u043D\u043E\u0435 \u0434\u043B\u044F \u0431\u0435\u0441\u043A\u043E\u043B\u043B\u0435\u043A\u0442\u043E\u0440\u043D\u044B\u0445 \u043C\u043E\u0442\u043E\u0440\u043E\u0432. \u041E\u0431\u043E\u0440\u043E\u0442\u044B \u043A\u0430\u0436\u0434\u043E\u0433\u043E \u043C\u043E\u0442\u043E\u0440\u0430 \u0437\u0430\u0434\u0430\u044E\u0442\u0441\u044F \u043A\u043E\u043C\u0430\u043D\u0434\u0430\u043C\u0438 \u043F\u043E\u043B\u0451\u0442\u043D\u043E\u0433\u043E \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440\u0430."]
        ]
      },
      {
        key: "motors",
        title: "\u041C\u043E\u0442\u043E\u0440\u044B \xD74",
        label: ["\u041C\u043E\u0442\u043E\u0440\u044B \xD74"],
        boxes: [[480, 170, 130, 70]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0422\u0438\u043F", "\u0431\u0435\u0441\u043A\u043E\u043B\u043B\u0435\u043A\u0442\u043E\u0440\u043D\u044B\u0439, outrunner"], ["\u0422\u0438\u043F\u043E\u0440\u0430\u0437\u043C\u0435\u0440", "2306 (\u0441\u0442\u0430\u0442\u043E\u0440 \u230023 \xD7 6 \u043C\u043C)"], ["KV", "1950 \u043E\u0431/\u043C\u0438\u043D/\u0412"], ["\u041F\u0438\u0442\u0430\u043D\u0438\u0435", "4\u20136S"], ["\u041C\u0430\u043A\u0441. \u0442\u043E\u043A", "40 \u0410"], ["\u041C\u0430\u0441\u0441\u0430", "32 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0421\u043E\u0437\u0434\u0430\u044E\u0442 \u0442\u044F\u0433\u0443, \u0432\u0440\u0430\u0449\u0430\u044F \u043F\u0440\u043E\u043F\u0435\u043B\u043B\u0435\u0440\u044B. \u041F\u0430\u0440\u044B \u043C\u043E\u0442\u043E\u0440\u043E\u0432 \u043F\u043E \u0434\u0438\u0430\u0433\u043E\u043D\u0430\u043B\u0438 \u0432\u0440\u0430\u0449\u0430\u044E\u0442\u0441\u044F \u0432 \u043F\u0440\u043E\u0442\u0438\u0432\u043E\u043F\u043E\u043B\u043E\u0436\u043D\u044B\u0435 \u0441\u0442\u043E\u0440\u043E\u043D\u044B \u2014 \u0442\u0430\u043A \u043A\u043E\u043C\u043F\u0435\u043D\u0441\u0438\u0440\u0443\u0435\u0442\u0441\u044F \u0440\u0435\u0430\u043A\u0442\u0438\u0432\u043D\u044B\u0439 \u043C\u043E\u043C\u0435\u043D\u0442."],
          ["\u0423\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u043E", "\u041F\u043E\u0434\u0440\u043E\u0431\u043D\u044B\u0439 \u0440\u0430\u0437\u0431\u043E\u0440 \u043C\u043E\u0442\u043E\u0440\u0430 \u2014 [\u0441\u0445\u0435\u043C\u0430 \xAB\u041C\u043E\u0442\u043E\u0440 2306\xBB](<#scheme/\u041C\u043E\u0442\u043E\u0440 2306 (\u0440\u0430\u0437\u043D\u0435\u0441\u0451\u043D\u043D\u044B\u0439 \u0432\u0438\u0434)>)."]
        ]
      },
      {
        key: "props",
        title: "\u041F\u0440\u043E\u043F\u0435\u043B\u043B\u0435\u0440\u044B \xD74",
        label: ["\u041F\u0440\u043E\u043F\u0435\u043B\u043B\u0435\u0440\u044B", "\xD74"],
        boxes: [[650, 170, 110, 70]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0414\u0438\u0430\u043C\u0435\u0442\u0440 \xD7 \u0448\u0430\u0433", "5,1\u2033 \xD7 3,6\u2033"], ["\u041B\u043E\u043F\u0430\u0441\u0442\u0435\u0439", "3"], ["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u043F\u043E\u043B\u0438\u043A\u0430\u0440\u0431\u043E\u043D\u0430\u0442"], ["\u041C\u0430\u0441\u0441\u0430", "4 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041F\u0440\u0435\u043E\u0431\u0440\u0430\u0437\u0443\u044E\u0442 \u0432\u0440\u0430\u0449\u0435\u043D\u0438\u0435 \u043C\u043E\u0442\u043E\u0440\u0430 \u0432 \u0442\u044F\u0433\u0443. \u041D\u0430\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435 \u0432\u0440\u0430\u0449\u0435\u043D\u0438\u044F \u043F\u0440\u043E\u043F\u0435\u043B\u043B\u0435\u0440\u0430 \u0434\u043E\u043B\u0436\u043D\u043E \u0441\u043E\u0432\u043F\u0430\u0434\u0430\u0442\u044C \u0441 \u043D\u0430\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u0435\u043C \u0432\u0440\u0430\u0449\u0435\u043D\u0438\u044F \u043C\u043E\u0442\u043E\u0440\u0430."]
        ]
      },
      {
        key: "rx",
        title: "\u041F\u0440\u0438\u0451\u043C\u043D\u0438\u043A \u0443\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u0438\u044F",
        label: ["\u041F\u0440\u0438\u0451\u043C\u043D\u0438\u043A"],
        boxes: [[40, 300, 160, 60]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041F\u0440\u043E\u0442\u043E\u043A\u043E\u043B", "ExpressLRS 2,4 \u0413\u0413\u0446"], ["\u0427\u0430\u0441\u0442\u043E\u0442\u0430 \u043F\u0430\u043A\u0435\u0442\u043E\u0432", "\u0434\u043E 500 \u0413\u0446"], ["\u0422\u0435\u043B\u0435\u043C\u0435\u0442\u0440\u0438\u044F", "\u0435\u0441\u0442\u044C"], ["\u0410\u043D\u0442\u0435\u043D\u043D\u044B", "2 (\u0440\u0430\u0437\u043D\u0435\u0441\u0451\u043D\u043D\u044B\u0439 \u043F\u0440\u0438\u0451\u043C)"], ["\u041C\u0430\u0441\u0441\u0430", "2 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041F\u0440\u0438\u043D\u0438\u043C\u0430\u0435\u0442 \u043A\u043E\u043C\u0430\u043D\u0434\u044B \u0441 \u043F\u0443\u043B\u044C\u0442\u0430 \u043E\u043F\u0435\u0440\u0430\u0442\u043E\u0440\u0430 \u0438 \u043F\u0435\u0440\u0435\u0434\u0430\u0451\u0442 \u0438\u0445 \u043F\u043E\u043B\u0451\u0442\u043D\u043E\u043C\u0443 \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440\u0443."]
        ]
      },
      {
        key: "gps",
        title: "GPS-\u043C\u043E\u0434\u0443\u043B\u044C",
        label: ["GPS + \u043A\u043E\u043C\u043F\u0430\u0441"],
        boxes: [[40, 390, 160, 60]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0427\u0438\u043F\u0441\u0435\u0442", "u-blox M10"], ["\u0421\u0438\u0441\u0442\u0435\u043C\u044B", "GPS / \u0413\u041B\u041E\u041D\u0410\u0421\u0421 / Galileo / BeiDou"], ["\u041A\u043E\u043C\u043F\u0430\u0441", "QMC5883L"], ["\u0427\u0430\u0441\u0442\u043E\u0442\u0430 \u043E\u0431\u043D\u043E\u0432\u043B\u0435\u043D\u0438\u044F", "10 \u0413\u0446"], ["\u041C\u0430\u0441\u0441\u0430", "9 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041E\u043F\u0440\u0435\u0434\u0435\u043B\u044F\u0435\u0442 \u043A\u043E\u043E\u0440\u0434\u0438\u043D\u0430\u0442\u044B \u0438 \u043A\u0443\u0440\u0441. \u0418\u0441\u043F\u043E\u043B\u044C\u0437\u0443\u0435\u0442\u0441\u044F \u0434\u043B\u044F \u0443\u0434\u0435\u0440\u0436\u0430\u043D\u0438\u044F \u043F\u043E\u0437\u0438\u0446\u0438\u0438, \u0432\u043E\u0437\u0432\u0440\u0430\u0442\u0430 \u0432 \u0442\u043E\u0447\u043A\u0443 \u0432\u0437\u043B\u0451\u0442\u0430 \u0438 \u0442\u0435\u043B\u0435\u043C\u0435\u0442\u0440\u0438\u0438."]
        ]
      },
      {
        key: "fc",
        title: "\u041F\u043E\u043B\u0451\u0442\u043D\u044B\u0439 \u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440",
        label: ["\u041F\u043E\u043B\u0451\u0442\u043D\u044B\u0439", "\u043A\u043E\u043D\u0442\u0440\u043E\u043B\u043B\u0435\u0440"],
        boxes: [[260, 330, 160, 90]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041F\u0440\u043E\u0446\u0435\u0441\u0441\u043E\u0440", "STM32F7, 216 \u041C\u0413\u0446"], ["\u0413\u0438\u0440\u043E\u0441\u043A\u043E\u043F / \u0430\u043A\u0441\u0435\u043B\u0435\u0440\u043E\u043C\u0435\u0442\u0440", "ICM-42688"], ["\u0411\u0430\u0440\u043E\u043C\u0435\u0442\u0440", "\u0435\u0441\u0442\u044C"], ["\u041F\u0440\u043E\u0448\u0438\u0432\u043A\u0430", "Betaflight / INAV / ArduPilot"], ["\u041C\u043E\u043D\u0442\u0430\u0436", "30,5 \xD7 30,5 \u043C\u043C"], ["\u041C\u0430\u0441\u0441\u0430", "8 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\xAB\u041C\u043E\u0437\u0433\xBB \u0430\u043F\u043F\u0430\u0440\u0430\u0442\u0430: \u043F\u043E \u0434\u0430\u043D\u043D\u044B\u043C \u0433\u0438\u0440\u043E\u0441\u043A\u043E\u043F\u0430 \u0441\u0442\u0430\u0431\u0438\u043B\u0438\u0437\u0438\u0440\u0443\u0435\u0442 \u043F\u043E\u043B\u043E\u0436\u0435\u043D\u0438\u0435, \u043E\u0431\u0440\u0430\u0431\u0430\u0442\u044B\u0432\u0430\u0435\u0442 \u043A\u043E\u043C\u0430\u043D\u0434\u044B \u043F\u0440\u0438\u0451\u043C\u043D\u0438\u043A\u0430, \u0443\u043F\u0440\u0430\u0432\u043B\u044F\u0435\u0442 ESC, \u043D\u0430\u043A\u043B\u0430\u0434\u044B\u0432\u0430\u0435\u0442 OSD \u043D\u0430 \u0432\u0438\u0434\u0435\u043E."]
        ]
      },
      {
        key: "camera",
        title: "\u041A\u0443\u0440\u0441\u043E\u0432\u0430\u044F \u043A\u0430\u043C\u0435\u0440\u0430",
        label: ["\u041A\u0430\u043C\u0435\u0440\u0430"],
        boxes: [[470, 300, 150, 60]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041C\u0430\u0442\u0440\u0438\u0446\u0430", "1/2\u2033 CMOS"], ["\u0412\u0438\u0434\u0435\u043E", "\u0430\u043D\u0430\u043B\u043E\u0433\u043E\u0432\u043E\u0435, PAL/NTSC"], ["\u0423\u0433\u043E\u043B \u043E\u0431\u0437\u043E\u0440\u0430", "155\xB0"], ["\u041C\u0430\u0441\u0441\u0430", "6 \u0433"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0414\u0430\u0451\u0442 \u043E\u043F\u0435\u0440\u0430\u0442\u043E\u0440\u0443 \u0438\u0437\u043E\u0431\u0440\u0430\u0436\u0435\u043D\u0438\u0435 \xAB\u043E\u0442 \u043F\u0435\u0440\u0432\u043E\u0433\u043E \u043B\u0438\u0446\u0430\xBB. \u041D\u0430\u043A\u043B\u043E\u043D\u0435\u043D\u0430 \u0432\u0432\u0435\u0440\u0445 \u043D\u0430 ~25\xB0, \u0447\u0442\u043E\u0431\u044B \u0432 \u0433\u043E\u0440\u0438\u0437\u043E\u043D\u0442\u0430\u043B\u044C\u043D\u043E\u043C \u043F\u043E\u043B\u0451\u0442\u0435 \u0441\u043C\u043E\u0442\u0440\u0435\u0442\u044C \u043F\u043E \u043A\u0443\u0440\u0441\u0443."]
        ]
      },
      {
        key: "vtx",
        title: "\u0412\u0438\u0434\u0435\u043E\u043F\u0435\u0440\u0435\u0434\u0430\u0442\u0447\u0438\u043A (VTX)",
        label: ["\u0412\u0438\u0434\u0435\u043E-", "\u043F\u0435\u0440\u0435\u0434\u0430\u0442\u0447\u0438\u043A"],
        boxes: [[470, 390, 150, 60]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0414\u0438\u0430\u043F\u0430\u0437\u043E\u043D", "5,8 \u0413\u0413\u0446"], ["\u041A\u0430\u043D\u0430\u043B\u043E\u0432", "48"], ["\u041C\u043E\u0449\u043D\u043E\u0441\u0442\u044C", "25\u2013800 \u043C\u0412\u0442"], ["\u0410\u043D\u0442\u0435\u043D\u043D\u0430", "\u043A\u0440\u0443\u0433\u043E\u0432\u0430\u044F \u043F\u043E\u043B\u044F\u0440\u0438\u0437\u0430\u0446\u0438\u044F (RHCP)"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041F\u0435\u0440\u0435\u0434\u0430\u0451\u0442 \u0432\u0438\u0434\u0435\u043E \u0441 \u043A\u0430\u043C\u0435\u0440\u044B (\u0441 \u043D\u0430\u043B\u043E\u0436\u0435\u043D\u043D\u044B\u043C OSD) \u043D\u0430 \u043E\u0447\u043A\u0438 \u0438\u043B\u0438 \u043C\u043E\u043D\u0438\u0442\u043E\u0440 \u043E\u043F\u0435\u0440\u0430\u0442\u043E\u0440\u0430."]
        ]
      }
    ]
  },
  {
    title: "\u041C\u043E\u0442\u043E\u0440 2306 (\u0440\u0430\u0437\u043D\u0435\u0441\u0451\u043D\u043D\u044B\u0439 \u0432\u0438\u0434)",
    model: "/models/motor.glb",
    image: "/schemes/motor.svg",
    size: [800, 520],
    parts: [
      {
        key: "shaft",
        title: "\u0412\u0430\u043B",
        label: ["\u0412\u0430\u043B"],
        boxes: [[140, 28, 640, 54]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0414\u0438\u0430\u043C\u0435\u0442\u0440", "5 \u043C\u043C (\u043F\u043E\u0434 \u043F\u0440\u043E\u043F\u0435\u043B\u043B\u0435\u0440 \u2014 \u0440\u0435\u0437\u044C\u0431\u0430 M5)"], ["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u0442\u0438\u0442\u0430\u043D, \u043F\u043E\u043B\u044B\u0439"], ["\u041A\u0440\u0435\u043F\u043B\u0435\u043D\u0438\u0435", "\u0441\u0442\u043E\u043F\u043E\u0440\u043D\u043E\u0435 \u043A\u043E\u043B\u044C\u0446\u043E \u0441\u043D\u0438\u0437\u0443"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041F\u0435\u0440\u0435\u0434\u0430\u0451\u0442 \u0432\u0440\u0430\u0449\u0435\u043D\u0438\u0435 \u043A\u043E\u043B\u043E\u043A\u043E\u043B\u0430 \u043D\u0430 \u043F\u0440\u043E\u043F\u0435\u043B\u043B\u0435\u0440. \u041F\u0440\u043E\u0445\u043E\u0434\u0438\u0442 \u0447\u0435\u0440\u0435\u0437 \u043F\u043E\u0434\u0448\u0438\u043F\u043D\u0438\u043A\u0438 \u0441\u0442\u0430\u0442\u043E\u0440\u0430."]
        ]
      },
      {
        key: "bell",
        title: "\u041A\u043E\u043B\u043E\u043A\u043E\u043B (\u0440\u043E\u0442\u043E\u0440)",
        label: ["\u041A\u043E\u043B\u043E\u043A\u043E\u043B (\u0440\u043E\u0442\u043E\u0440)"],
        boxes: [[140, 90, 640, 84]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u0430\u043B\u044E\u043C\u0438\u043D\u0438\u0439 7075, \u0430\u043D\u043E\u0434\u0438\u0440\u043E\u0432\u0430\u043D\u0438\u0435"], ["\u0414\u0438\u0430\u043C\u0435\u0442\u0440", "27,9 \u043C\u043C"], ["\u0421\u043E\u0435\u0434\u0438\u043D\u0435\u043D\u0438\u0435 \u0441 \u0432\u0430\u043B\u043E\u043C", "\u0432\u0438\u043D\u0442 \u0441 \u0444\u0438\u043A\u0441\u0430\u0442\u043E\u0440\u043E\u043C"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0412\u0440\u0430\u0449\u0430\u044E\u0449\u0430\u044F\u0441\u044F \u0447\u0430\u0441\u0442\u044C \u043C\u043E\u0442\u043E\u0440\u0430 \u0442\u0438\u043F\u0430 outrunner. \u041D\u0430 \u0432\u043D\u0443\u0442\u0440\u0435\u043D\u043D\u0435\u0439 \u0441\u0442\u0435\u043D\u043A\u0435 \u0432\u043A\u043B\u0435\u0435\u043D\u044B \u043C\u0430\u0433\u043D\u0438\u0442\u044B."]
        ]
      },
      {
        key: "magnets",
        title: "\u041C\u0430\u0433\u043D\u0438\u0442\u044B",
        label: ["\u041C\u0430\u0433\u043D\u0438\u0442\u044B (14 \u043F\u043E\u043B\u044E\u0441\u043E\u0432)"],
        boxes: [[140, 182, 640, 60]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041A\u043E\u043B\u0438\u0447\u0435\u0441\u0442\u0432\u043E", "14 (\u0441\u0445\u0435\u043C\u0430 12N14P)"], ["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "NdFeB, \u043A\u043B\u0430\u0441\u0441 N52"], ["\u0424\u043E\u0440\u043C\u0430", "\u0434\u0443\u0433\u043E\u0432\u044B\u0435 \u0441\u0435\u0433\u043C\u0435\u043D\u0442\u044B"]])],
          ["\u041D\u0435\u0438\u0441\u043F\u0440\u0430\u0432\u043D\u043E\u0441\u0442\u0438", "\u041E\u0442\u043A\u043E\u043B\u043E\u0432\u0448\u0438\u0439\u0441\u044F \u043C\u0430\u0433\u043D\u0438\u0442 \u0434\u0430\u0451\u0442 \u0432\u0438\u0431\u0440\u0430\u0446\u0438\u044E \u0438 \xAB\u0440\u044B\u0447\u0430\u043D\u0438\u0435\xBB \u043C\u043E\u0442\u043E\u0440\u0430 \u2014 \u043A\u043E\u043B\u043E\u043A\u043E\u043B \u043F\u043E\u0434\u043B\u0435\u0436\u0438\u0442 \u0437\u0430\u043C\u0435\u043D\u0435."]
        ]
      },
      {
        key: "bearings",
        title: "\u041F\u043E\u0434\u0448\u0438\u043F\u043D\u0438\u043A\u0438",
        label: ["\u041F\u043E\u0434\u0448\u0438\u043F\u043D\u0438\u043A\u0438 \xD72"],
        boxes: [[140, 250, 640, 48]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0422\u0438\u043F", "\u0440\u0430\u0434\u0438\u0430\u043B\u044C\u043D\u044B\u0435 \u0448\u0430\u0440\u0438\u043A\u043E\u0432\u044B\u0435"], ["\u0420\u0430\u0437\u043C\u0435\u0440", "9 \xD7 4 \xD7 4 \u043C\u043C"], ["\u041A\u043E\u043B\u0438\u0447\u0435\u0441\u0442\u0432\u043E", "2"]])],
          ["\u042D\u043A\u0441\u043F\u043B\u0443\u0430\u0442\u0430\u0446\u0438\u044F", "\u041B\u044E\u0444\u0442 \u0432\u0430\u043B\u0430 \u0438\u043B\u0438 \xAB\u0445\u0440\u0443\u0441\u0442\xBB \u043F\u0440\u0438 \u043F\u0440\u043E\u043A\u0440\u0443\u0442\u043A\u0435 \u0440\u0443\u043A\u043E\u0439 \u2014 \u043F\u0440\u0438\u0437\u043D\u0430\u043A \u0438\u0437\u043D\u043E\u0441\u0430 \u043F\u043E\u0434\u0448\u0438\u043F\u043D\u0438\u043A\u043E\u0432."]
        ]
      },
      {
        key: "stator",
        title: "\u0421\u0442\u0430\u0442\u043E\u0440 \u0441 \u043E\u0431\u043C\u043E\u0442\u043A\u0430\u043C\u0438",
        label: ["\u0421\u0442\u0430\u0442\u043E\u0440 \u0441 \u043E\u0431\u043C\u043E\u0442\u043A\u0430\u043C\u0438"],
        boxes: [[140, 306, 640, 92]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u0420\u0430\u0437\u043C\u0435\u0440", "\u230023 \xD7 6 \u043C\u043C"], ["\u0417\u0443\u0431\u0446\u043E\u0432", "12"], ["\u041F\u0430\u043A\u0435\u0442", "\u044D\u043B\u0435\u043A\u0442\u0440\u043E\u0442\u0435\u0445\u043D\u0438\u0447\u0435\u0441\u043A\u0430\u044F \u0441\u0442\u0430\u043B\u044C, \u043F\u043B\u0430\u0441\u0442\u0438\u043D\u044B 0,2 \u043C\u043C"], ["\u041E\u0431\u043C\u043E\u0442\u043A\u0430", "\u043C\u0435\u0434\u043D\u044B\u0439 \u043B\u0430\u043A\u0438\u0440\u043E\u0432\u0430\u043D\u043D\u044B\u0439 \u043F\u0440\u043E\u0432\u043E\u0434, \xAB\u0437\u0432\u0435\u0437\u0434\u0430\xBB"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041D\u0435\u043F\u043E\u0434\u0432\u0438\u0436\u043D\u0430\u044F \u0447\u0430\u0441\u0442\u044C \u043C\u043E\u0442\u043E\u0440\u0430. \u0420\u0435\u0433\u0443\u043B\u044F\u0442\u043E\u0440 \u043F\u043E\u043E\u0447\u0435\u0440\u0451\u0434\u043D\u043E \u043F\u043E\u0434\u0430\u0451\u0442 \u0442\u043E\u043A \u043D\u0430 \u043E\u0431\u043C\u043E\u0442\u043A\u0438, \u0441\u043E\u0437\u0434\u0430\u0432\u0430\u044F \u0432\u0440\u0430\u0449\u0430\u044E\u0449\u0435\u0435\u0441\u044F \u043C\u0430\u0433\u043D\u0438\u0442\u043D\u043E\u0435 \u043F\u043E\u043B\u0435."]
        ]
      },
      {
        key: "base",
        title: "\u041E\u0441\u043D\u043E\u0432\u0430\u043D\u0438\u0435",
        label: ["\u041E\u0441\u043D\u043E\u0432\u0430\u043D\u0438\u0435 + \u0432\u044B\u0432\u043E\u0434\u044B \u0444\u0430\u0437"],
        boxes: [[140, 406, 640, 64]],
        sections: [
          ["\u0422\u0422\u0425", ttx([["\u041C\u043E\u043D\u0442\u0430\u0436", "16 \xD7 16 \u043C\u043C, 4 \xD7 M3"], ["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u0430\u043B\u044E\u043C\u0438\u043D\u0438\u0439"], ["\u0412\u044B\u0432\u043E\u0434\u044B", "3 \u0444\u0430\u0437\u043D\u044B\u0445 \u043F\u0440\u043E\u0432\u043E\u0434\u0430"]])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041A\u0440\u0435\u043F\u0438\u0442 \u043C\u043E\u0442\u043E\u0440 \u043A \u043B\u0443\u0447\u0443 \u0440\u0430\u043C\u044B \u0438 \u0434\u0435\u0440\u0436\u0438\u0442 \u0441\u0442\u0430\u0442\u043E\u0440. \u0427\u0435\u0440\u0435\u0437 \u043E\u0441\u043D\u043E\u0432\u0430\u043D\u0438\u0435 \u0432\u044B\u0432\u0435\u0434\u0435\u043D\u044B \u043F\u0440\u043E\u0432\u043E\u0434\u0430 \u0444\u0430\u0437 \u043A ESC."]
        ]
      }
    ]
  }
];
var UAV_SCHEMES = [
  {
    title: "\u041E\u0431\u0449\u0438\u0439 \u0432\u0438\u0434",
    model: "/models/uav.glb",
    image: "/schemes/uav-drawing.png",
    size: [741, 864],
    parts: [
      {
        key: "wing",
        title: "\u041A\u0440\u044B\u043B\u043E",
        boxes: [[12, 20, 150, 62], [330, 165, 160, 62]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u0420\u0430\u0437\u043C\u0430\u0445", "\u041F\u043B\u043E\u0449\u0430\u0434\u044C", "\u041F\u0440\u043E\u0444\u0438\u043B\u044C", "\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u041C\u0430\u0441\u0441\u0430"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0421\u043E\u0437\u0434\u0430\u0451\u0442 \u043F\u043E\u0434\u044A\u0451\u043C\u043D\u0443\u044E \u0441\u0438\u043B\u0443. \u041F\u0440\u044F\u043C\u043E\u0435 \u043A\u0440\u044B\u043B\u043E \u0431\u043E\u043B\u044C\u0448\u043E\u0433\u043E \u0443\u0434\u043B\u0438\u043D\u0435\u043D\u0438\u044F, \u0430\u043F\u043F\u0430\u0440\u0430\u0442 \u0432\u044B\u043F\u043E\u043B\u043D\u0435\u043D \u043F\u043E \u0441\u0445\u0435\u043C\u0435 \u0432\u044B\u0441\u043E\u043A\u043E\u043F\u043B\u0430\u043D\u0430. \u041A \u043A\u0440\u044B\u043B\u0443 \u043A\u0440\u0435\u043F\u044F\u0442\u0441\u044F \u0445\u0432\u043E\u0441\u0442\u043E\u0432\u044B\u0435 \u0431\u0430\u043B\u043A\u0438 \u0438 \u0433\u043E\u043D\u0434\u043E\u043B\u0430 \u0434\u0432\u0438\u0433\u0430\u0442\u0435\u043B\u044F."]
        ]
      },
      {
        key: "booms",
        title: "\u0425\u0432\u043E\u0441\u0442\u043E\u0432\u044B\u0435 \u0431\u0430\u043B\u043A\u0438",
        boxes: [[322, 92, 98, 40], [205, 64, 100, 32]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u0414\u043B\u0438\u043D\u0430", "\u0414\u0438\u0430\u043C\u0435\u0442\u0440", "\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0414\u0432\u0435 \u0431\u0430\u043B\u043A\u0438 \u0441\u043E\u0435\u0434\u0438\u043D\u044F\u044E\u0442 \u043A\u0440\u044B\u043B\u043E \u0441 \u0445\u0432\u043E\u0441\u0442\u043E\u0432\u044B\u043C \u043E\u043F\u0435\u0440\u0435\u043D\u0438\u0435\u043C. \u0414\u0432\u0443\u0445\u0431\u0430\u043B\u043E\u0447\u043D\u0430\u044F \u0441\u0445\u0435\u043C\u0430 \u043E\u0441\u0442\u0430\u0432\u043B\u044F\u0435\u0442 \u043C\u0435\u0441\u0442\u043E \u0434\u043B\u044F \u0442\u043E\u043B\u043A\u0430\u044E\u0449\u0435\u0433\u043E \u0432\u0438\u043D\u0442\u0430 \u043F\u043E\u0437\u0430\u0434\u0438 \u043A\u0440\u044B\u043B\u0430."]
        ]
      },
      {
        key: "tail",
        title: "\u0425\u0432\u043E\u0441\u0442\u043E\u0432\u043E\u0435 \u043E\u043F\u0435\u0440\u0435\u043D\u0438\u0435",
        boxes: [[318, 2, 142, 50]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u0420\u0430\u0437\u043C\u0430\u0445 \u0441\u0442\u0430\u0431\u0438\u043B\u0438\u0437\u0430\u0442\u043E\u0440\u0430", "\u0412\u044B\u0441\u043E\u0442\u0430 \u043A\u0438\u043B\u0435\u0439", "\u0420\u0443\u043B\u0438"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0414\u0432\u0430 \u043A\u0438\u043B\u044F \u043D\u0430 \u043A\u043E\u043D\u0446\u0430\u0445 \u0431\u0430\u043B\u043E\u043A \u0438 \u0441\u0442\u0430\u0431\u0438\u043B\u0438\u0437\u0430\u0442\u043E\u0440 \u043C\u0435\u0436\u0434\u0443 \u043D\u0438\u043C\u0438. \u041E\u0431\u0435\u0441\u043F\u0435\u0447\u0438\u0432\u0430\u0435\u0442 \u0443\u0441\u0442\u043E\u0439\u0447\u0438\u0432\u043E\u0441\u0442\u044C \u0438 \u0443\u043F\u0440\u0430\u0432\u043B\u044F\u0435\u043C\u043E\u0441\u0442\u044C \u043F\u043E \u0442\u0430\u043D\u0433\u0430\u0436\u0443 \u0438 \u043A\u0443\u0440\u0441\u0443."]
        ]
      },
      {
        key: "fuselage",
        title: "\u0424\u044E\u0437\u0435\u043B\u044F\u0436 (\u0446\u0435\u043D\u0442\u0440\u0430\u043B\u044C\u043D\u0430\u044F \u0447\u0430\u0441\u0442\u044C)",
        boxes: [[196, 122, 56, 60]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u0414\u043B\u0438\u043D\u0430", "\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0426\u0435\u043D\u0442\u0440\u0430\u043B\u044C\u043D\u0430\u044F \u0447\u0430\u0441\u0442\u044C \u0444\u044E\u0437\u0435\u043B\u044F\u0436\u0430: \u0441\u0432\u044F\u0437\u044B\u0432\u0430\u0435\u0442 \u043D\u043E\u0441\u043E\u0432\u0443\u044E \u0447\u0430\u0441\u0442\u044C, \u043A\u0440\u044B\u043B\u043E \u0438 \u0441\u0438\u043B\u043E\u0432\u0443\u044E \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0443. \u0421\u0442\u044B\u043A\u0443\u0435\u0442\u0441\u044F \u0441 \u043D\u043E\u0441\u043E\u0432\u043E\u0439 \u0447\u0430\u0441\u0442\u044C\u044E \u043F\u043E \u0448\u043F\u0430\u043D\u0433\u043E\u0443\u0442\u0443 \u0428\u043F.2."]
        ]
      },
      {
        key: "engine",
        title: "\u0421\u0438\u043B\u043E\u0432\u0430\u044F \u0443\u0441\u0442\u0430\u043D\u043E\u0432\u043A\u0430",
        boxes: [[248, 78, 70, 72]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u0422\u0438\u043F \u0434\u0432\u0438\u0433\u0430\u0442\u0435\u043B\u044F", "\u041C\u043E\u0449\u043D\u043E\u0441\u0442\u044C", "\u0412\u043E\u0437\u0434\u0443\u0448\u043D\u044B\u0439 \u0432\u0438\u043D\u0442", "\u0422\u043E\u043F\u043B\u0438\u0432\u043E / \u043F\u0438\u0442\u0430\u043D\u0438\u0435"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0414\u0432\u0438\u0433\u0430\u0442\u0435\u043B\u044C \u0432 \u0433\u043E\u043D\u0434\u043E\u043B\u0435 \u043D\u0430\u0434 \u0446\u0435\u043D\u0442\u0440\u043E\u043F\u043B\u0430\u043D\u043E\u043C \u0441 \u0442\u043E\u043B\u043A\u0430\u044E\u0449\u0438\u043C \u0432\u043E\u0437\u0434\u0443\u0448\u043D\u044B\u043C \u0432\u0438\u043D\u0442\u043E\u043C \u043F\u043E\u0437\u0430\u0434\u0438 \u043A\u0440\u044B\u043B\u0430."]
        ]
      },
      {
        key: "gear",
        title: "\u0428\u0430\u0441\u0441\u0438",
        boxes: [[258, 158, 34, 40], [128, 206, 26, 24]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u0421\u0445\u0435\u043C\u0430", "\u041A\u043E\u043B\u0435\u044F", "\u041A\u043E\u043B\u0451\u0441\u0430"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0422\u0440\u0451\u0445\u043E\u043F\u043E\u0440\u043D\u043E\u0435 \u0448\u0430\u0441\u0441\u0438: \u043D\u043E\u0441\u043E\u0432\u0430\u044F \u0441\u0442\u043E\u0439\u043A\u0430 \u0438 \u0434\u0432\u0435 \u043E\u0441\u043D\u043E\u0432\u043D\u044B\u0435 \u0440\u0435\u0441\u0441\u043E\u0440\u043D\u044B\u0435 \u0441\u0442\u043E\u0439\u043A\u0438."]
        ]
      },
      {
        key: "nose",
        title: "\u041D\u043E\u0441\u043E\u0432\u0430\u044F \u0447\u0430\u0441\u0442\u044C",
        boxes: [[116, 140, 86, 74], [60, 300, 681, 564]],
        sections: [
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041D\u043E\u0441\u043E\u0432\u043E\u0439 \u043E\u0442\u0441\u0435\u043A \u0444\u044E\u0437\u0435\u043B\u044F\u0436\u0430: \u043E\u0431\u0448\u0438\u0432\u043A\u0430 \u0438 \u0441\u0438\u043B\u043E\u0432\u043E\u0439 \u043A\u0430\u0440\u043A\u0430\u0441 (\u0448\u043F\u0430\u043D\u0433\u043E\u0443\u0442\u044B \u0428\u043F.1 \u0438 \u0428\u043F.2). \u0412\u043D\u0443\u0442\u0440\u0438 \u0440\u0430\u0437\u043C\u0435\u0449\u0430\u0435\u0442\u0441\u044F \u0431\u043E\u0440\u0442\u043E\u0432\u043E\u0435 \u043E\u0431\u043E\u0440\u0443\u0434\u043E\u0432\u0430\u043D\u0438\u0435, \u0432 \u0442\u043E\u043C \u0447\u0438\u0441\u043B\u0435 \u0431\u043B\u043E\u043A \u0414\u0410341."],
          ["\u0423\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u043E", "\u041F\u043E\u0434\u0440\u043E\u0431\u043D\u044B\u0439 \u0440\u0430\u0437\u0431\u043E\u0440 \u2014 [\u0441\u0445\u0435\u043C\u0430 \xAB\u041D\u043E\u0441\u043E\u0432\u0430\u044F \u0447\u0430\u0441\u0442\u044C\xBB](<#scheme/\u041D\u043E\u0441\u043E\u0432\u0430\u044F \u0447\u0430\u0441\u0442\u044C>)."]
        ]
      },
      {
        key: "antenna",
        title: "\u0410\u043D\u0442\u0435\u043D\u043D\u0430",
        boxes: [[164, 114, 24, 38]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0414\u0438\u0430\u043F\u0430\u0437\u043E\u043D", "\u0422\u0438\u043F"])],
          ["\u0420\u0430\u0437\u043C\u0435\u0449\u0435\u043D\u0438\u0435", "\u041D\u0430 \u0432\u0435\u0440\u0445\u043D\u0435\u0439 \u043F\u043E\u0432\u0435\u0440\u0445\u043D\u043E\u0441\u0442\u0438 \u0444\u044E\u0437\u0435\u043B\u044F\u0436\u0430, \u0441\u0440\u0430\u0437\u0443 \u0437\u0430 \u043D\u043E\u0441\u043E\u0432\u043E\u0439 \u0447\u0430\u0441\u0442\u044C\u044E."]
        ]
      }
    ]
  },
  {
    title: "\u041D\u043E\u0441\u043E\u0432\u0430\u044F \u0447\u0430\u0441\u0442\u044C",
    model: "/models/uav-nose.glb",
    image: "/schemes/uav-nose.svg",
    size: [681, 564],
    parts: [
      {
        key: "obshivka",
        title: "\u041E\u0431\u0448\u0438\u0432\u043A\u0430 \u043D\u043E\u0441\u043E\u0432\u043E\u0439 \u0447\u0430\u0441\u0442\u0438",
        boxes: [[2, 320, 68, 155]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u0422\u043E\u043B\u0449\u0438\u043D\u0430"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0412\u043D\u0435\u0448\u043D\u044F\u044F \u043E\u0431\u043E\u043B\u043E\u0447\u043A\u0430 \u043E\u0442\u0441\u0435\u043A\u0430: \u043E\u0431\u0440\u0430\u0437\u0443\u0435\u0442 \u0430\u044D\u0440\u043E\u0434\u0438\u043D\u0430\u043C\u0438\u0447\u0435\u0441\u043A\u0438\u0439 \u043E\u0431\u0432\u043E\u0434 \u0438 \u0437\u0430\u0449\u0438\u0449\u0430\u0435\u0442 \u043E\u0431\u043E\u0440\u0443\u0434\u043E\u0432\u0430\u043D\u0438\u0435. \u0412 \u043C\u043E\u0434\u0435\u043B\u0438 \u043F\u043E\u043A\u0430\u0437\u0430\u043D\u0430 \u043F\u043E\u043B\u0443\u043F\u0440\u043E\u0437\u0440\u0430\u0447\u043D\u043E\u0439, \u0447\u0442\u043E\u0431\u044B \u0431\u044B\u043B\u043E \u0432\u0438\u0434\u043D\u043E \u0432\u043D\u0443\u0442\u0440\u0435\u043D\u043D\u0435\u0435 \u0443\u0441\u0442\u0440\u043E\u0439\u0441\u0442\u0432\u043E."]
        ]
      },
      {
        key: "shp1",
        title: "\u0428\u043F\u0430\u043D\u0433\u043E\u0443\u0442 \u0428\u043F.1",
        boxes: [[72, 315, 228, 240]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u0422\u043E\u043B\u0449\u0438\u043D\u0430", "\u041C\u0430\u0441\u0441\u0430"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041F\u0435\u0440\u0435\u0434\u043D\u0438\u0439 \u043F\u043E\u043F\u0435\u0440\u0435\u0447\u043D\u044B\u0439 \u0441\u0438\u043B\u043E\u0432\u043E\u0439 \u044D\u043B\u0435\u043C\u0435\u043D\u0442 \u043E\u0442\u0441\u0435\u043A\u0430. \u0417\u0430\u0434\u0430\u0451\u0442 \u0444\u043E\u0440\u043C\u0443 \u0441\u0435\u0447\u0435\u043D\u0438\u044F \u0438 \u0432\u043E\u0441\u043F\u0440\u0438\u043D\u0438\u043C\u0430\u0435\u0442 \u043D\u0430\u0433\u0440\u0443\u0437\u043A\u0438 \u043E\u0442 \u043E\u0431\u0448\u0438\u0432\u043A\u0438; \u043F\u0435\u0440\u0435\u0434 \u043D\u0438\u043C \u2014 \u043D\u043E\u0441\u043E\u0432\u043E\u0439 \u043E\u0431\u0442\u0435\u043A\u0430\u0442\u0435\u043B\u044C."]
        ]
      },
      {
        key: "shp2",
        title: "\u0428\u043F\u0430\u043D\u0433\u043E\u0443\u0442 \u0428\u043F.2",
        boxes: [[445, 18, 232, 375]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u041C\u0430\u0442\u0435\u0440\u0438\u0430\u043B", "\u0422\u043E\u043B\u0449\u0438\u043D\u0430", "\u041C\u0430\u0441\u0441\u0430"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u0417\u0430\u0434\u043D\u0438\u0439 \u0448\u043F\u0430\u043D\u0433\u043E\u0443\u0442 \u043D\u043E\u0441\u043E\u0432\u043E\u0439 \u0447\u0430\u0441\u0442\u0438 \u2014 \u0441\u0442\u044B\u043A \u0441 \u0446\u0435\u043D\u0442\u0440\u0430\u043B\u044C\u043D\u043E\u0439 \u0447\u0430\u0441\u0442\u044C\u044E \u0444\u044E\u0437\u0435\u043B\u044F\u0436\u0430. \u0418\u043C\u0435\u0435\u0442 \u043E\u0431\u043B\u0435\u0433\u0447\u0430\u044E\u0449\u0435\u0435 \u043E\u0442\u0432\u0435\u0440\u0441\u0442\u0438\u0435 \u0438 \u043E\u0442\u0432\u0435\u0440\u0441\u0442\u0438\u044F \u0434\u043B\u044F \u043F\u0440\u043E\u043A\u043B\u0430\u0434\u043A\u0438 \u0436\u0433\u0443\u0442\u043E\u0432."]
        ]
      },
      {
        key: "lyuk",
        title: "\u042D\u043A\u0441\u043F\u043B\u0443\u0430\u0442\u0430\u0446\u0438\u043E\u043D\u043D\u044B\u0439 \u043B\u044E\u043A",
        boxes: [[335, 78, 108, 95]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u0420\u0430\u0437\u043C\u0435\u0440 \u043F\u0440\u043E\u0451\u043C\u0430", "\u041A\u0440\u0435\u043F\u043B\u0435\u043D\u0438\u0435 \u043A\u0440\u044B\u0448\u043A\u0438"])],
          ["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041B\u044E\u043A \u0432 \u0432\u0435\u0440\u0445\u043D\u0435\u0439 \u043F\u0430\u043D\u0435\u043B\u0438 \u0434\u043B\u044F \u0434\u043E\u0441\u0442\u0443\u043F\u0430 \u043A \u043E\u0431\u043E\u0440\u0443\u0434\u043E\u0432\u0430\u043D\u0438\u044E \u043D\u043E\u0441\u043E\u0432\u043E\u0433\u043E \u043E\u0442\u0441\u0435\u043A\u0430 \u043F\u0440\u0438 \u043E\u0431\u0441\u043B\u0443\u0436\u0438\u0432\u0430\u043D\u0438\u0438."]
        ]
      },
      {
        key: "blok_da341",
        title: "\u0411\u043B\u043E\u043A \u0414\u0410341",
        boxes: [[10, 170, 335, 148]],
        sections: [
          ["\u0422\u0422\u0425", todo(["\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435", "\u041D\u0430\u043F\u0440\u044F\u0436\u0435\u043D\u0438\u0435 \u043F\u0438\u0442\u0430\u043D\u0438\u044F", "\u041F\u043E\u0442\u0440\u0435\u0431\u043B\u044F\u0435\u043C\u0430\u044F \u043C\u043E\u0449\u043D\u043E\u0441\u0442\u044C", "\u0413\u0430\u0431\u0430\u0440\u0438\u0442\u044B", "\u041C\u0430\u0441\u0441\u0430", "\u0420\u0430\u0437\u044A\u0451\u043C\u044B"])],
          ["\u0420\u0430\u0437\u043C\u0435\u0449\u0435\u043D\u0438\u0435", "\u0423\u0441\u0442\u0430\u043D\u043E\u0432\u043B\u0435\u043D \u0432 \u043D\u043E\u0441\u043E\u0432\u043E\u043C \u043E\u0442\u0441\u0435\u043A\u0435 \u043D\u0430 \u043A\u0440\u043E\u043D\u0448\u0442\u0435\u0439\u043D\u0435 \u043F\u043E\u0434 \u0432\u0435\u0440\u0445\u043D\u0435\u0439 \u043F\u0430\u043D\u0435\u043B\u044C\u044E, \u043C\u0435\u0436\u0434\u0443 \u0428\u043F.1 \u0438 \u044D\u043A\u0441\u043F\u043B\u0443\u0430\u0442\u0430\u0446\u0438\u043E\u043D\u043D\u044B\u043C \u043B\u044E\u043A\u043E\u043C."]
        ]
      }
    ]
  }
];
var DEVICES = [
  { title: "\u0411\u041F\u041B\u0410 (\u043F\u043E \u0447\u0435\u0440\u0442\u0435\u0436\u0443)", schemes: UAV_SCHEMES },
  { title: "\u041A\u0432\u0430\u0434\u0440\u043E\u043A\u043E\u043F\u0442\u0435\u0440 (\u0443\u0447\u0435\u0431\u043D\u0430\u044F \u043C\u043E\u0434\u0435\u043B\u044C)", schemes: QUAD_SCHEMES }
];
var USERS = [
  { login: "user", password: "user", role: "user" },
  { login: "admin", password: "admin", role: "admin" }
];

// server/db.ts
mkdirSync(UPLOADS_DIR, { recursive: true });
var db = new DatabaseSync(DB_FILE);
db.exec("PRAGMA journal_mode = WAL; PRAGMA foreign_keys = ON;");
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id        INTEGER PRIMARY KEY,
  login     TEXT NOT NULL UNIQUE,
  pass_hash TEXT NOT NULL,
  role      TEXT NOT NULL CHECK (role IN ('user', 'admin'))
);
CREATE TABLE IF NOT EXISTS sessions (
  token      TEXT PRIMARY KEY,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS devices (
  id    INTEGER PRIMARY KEY,
  title TEXT NOT NULL UNIQUE
);
-- \u0431\u043B\u043E\u043A-\u0441\u0445\u0435\u043C\u0430 = 3D-\u043C\u043E\u0434\u0435\u043B\u044C (.glb) + \u043A\u0430\u0440\u0442\u0438\u043D\u043A\u0430 \u0441\u0445\u0435\u043C\u044B
CREATE TABLE IF NOT EXISTS schemes (
  id        INTEGER PRIMARY KEY,
  device_id INTEGER NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  title     TEXT NOT NULL,
  model_url TEXT NOT NULL,
  image_url TEXT NOT NULL,
  sort      INTEGER NOT NULL DEFAULT 0
);
-- \u0434\u0435\u0442\u0430\u043B\u044C: key = \u0438\u043C\u044F \u0443\u0437\u043B\u0430 \u0432 .glb
CREATE TABLE IF NOT EXISTS parts (
  id        INTEGER PRIMARY KEY,
  scheme_id INTEGER NOT NULL REFERENCES schemes(id) ON DELETE CASCADE,
  key       TEXT NOT NULL,
  title     TEXT NOT NULL,
  UNIQUE (scheme_id, key)
);
-- \u043A\u043B\u0438\u043A\u0430\u0431\u0435\u043B\u044C\u043D\u044B\u0435 \u043E\u0431\u043B\u0430\u0441\u0442\u0438 \u0434\u0435\u0442\u0430\u043B\u0438 \u043D\u0430 \u043A\u0430\u0440\u0442\u0438\u043D\u043A\u0435 \u0441\u0445\u0435\u043C\u044B, \u0434\u043E\u043B\u0438 0..1 (\u0438\u0445 \u043C\u043E\u0436\u0435\u0442 \u0431\u044B\u0442\u044C \u043D\u0435\u0441\u043A\u043E\u043B\u044C\u043A\u043E)
CREATE TABLE IF NOT EXISTS hotspots (
  id      INTEGER PRIMARY KEY,
  part_id INTEGER NOT NULL REFERENCES parts(id) ON DELETE CASCADE,
  x REAL NOT NULL, y REAL NOT NULL, w REAL NOT NULL, h REAL NOT NULL
);
-- \u0440\u0430\u0437\u0434\u0435\u043B\u044B \u043A\u0430\u0440\u0442\u043E\u0447\u043A\u0438 \u0434\u0435\u0442\u0430\u043B\u0438 (\xAB\u0422\u0422\u0425\xBB, \xAB\u041D\u0430\u0437\u043D\u0430\u0447\u0435\u043D\u0438\u0435\xBB, \u2026), \u0442\u0435\u043A\u0441\u0442 \u0432 Markdown
CREATE TABLE IF NOT EXISTS sections (
  id      INTEGER PRIMARY KEY,
  part_id INTEGER NOT NULL REFERENCES parts(id) ON DELETE CASCADE,
  title   TEXT NOT NULL,
  body    TEXT NOT NULL DEFAULT '',
  sort    INTEGER NOT NULL DEFAULT 0
);
`);
var all = (sql, ...params) => db.prepare(sql).all(...params);
var get = (sql, ...params) => db.prepare(sql).get(...params);
var run = (sql, ...params) => db.prepare(sql).run(...params);
function tx(fn) {
  db.exec("BEGIN");
  try {
    const result = fn();
    db.exec("COMMIT");
    return result;
  } catch (e) {
    db.exec("ROLLBACK");
    throw e;
  }
}
function hashPassword(password) {
  const salt = randomBytes(16);
  return `${salt.toString("hex")}:${scryptSync(password, salt, 32).toString("hex")}`;
}
function checkPassword(password, stored) {
  const [salt, hash] = stored.split(":");
  const expected = Buffer.from(hash, "hex");
  const actual = scryptSync(password, Buffer.from(salt, "hex"), 32);
  return expected.length === actual.length && timingSafeEqual(expected, actual);
}
function seed() {
  tx(() => {
    for (const u of USERS) run("INSERT INTO users (login, pass_hash, role) VALUES (?, ?, ?)", u.login, hashPassword(u.password), u.role);
    for (const device of DEVICES) {
      const deviceId = run("INSERT INTO devices (title) VALUES (?)", device.title).lastInsertRowid;
      device.schemes.forEach((s, i) => {
        const schemeId = run("INSERT INTO schemes (device_id, title, model_url, image_url, sort) VALUES (?, ?, ?, ?, ?)", deviceId, s.title, s.model, s.image, i).lastInsertRowid;
        const [W, H] = s.size;
        for (const p of s.parts) {
          const partId = run("INSERT INTO parts (scheme_id, key, title) VALUES (?, ?, ?)", schemeId, p.key, p.title).lastInsertRowid;
          for (const [x, y, w, h] of p.boxes) run("INSERT INTO hotspots (part_id, x, y, w, h) VALUES (?, ?, ?, ?, ?)", partId, x / W, y / H, w / W, h / H);
          p.sections.forEach(([title, body], j) => run("INSERT INTO sections (part_id, title, body, sort) VALUES (?, ?, ?, ?)", partId, title, body, j));
        }
      });
    }
  });
  console.log("  \u0411\u0414 \u0441\u043E\u0437\u0434\u0430\u043D\u0430 \u0438 \u0437\u0430\u043F\u043E\u043B\u043D\u0435\u043D\u0430 \u0434\u0435\u043C\u043E-\u0434\u0430\u043D\u043D\u044B\u043C\u0438");
}
if (!get("SELECT 1 FROM users LIMIT 1")) seed();

// server/index.ts
process.chdir(ROOT);
var hotspotsOf = (partId) => all("SELECT id, x, y, w, h FROM hotspots WHERE part_id = ? ORDER BY id", partId);
var toPart = (r) => ({ ...r, hotspots: hotspotsOf(r.id) });
var partById = (id) => {
  const row = get("SELECT id, key, title FROM parts WHERE id = ?", id);
  return row && toPart(row);
};
var str = (v) => typeof v === "string" ? v.trim() : "";
var api = new Hono2();
api.post("/login", async (c) => {
  const body = await c.req.json().catch(() => ({}));
  const row = get("SELECT id, login, role, pass_hash FROM users WHERE login = ?", str(body.login));
  if (!row || !checkPassword(str(body.password), row.pass_hash)) return c.json({ error: "\u041D\u0435\u0432\u0435\u0440\u043D\u044B\u0439 \u043B\u043E\u0433\u0438\u043D \u0438\u043B\u0438 \u043F\u0430\u0440\u043E\u043B\u044C" }, 401);
  const token = randomBytes2(24).toString("hex");
  run("INSERT INTO sessions (token, user_id, created_at) VALUES (?, ?, ?)", token, row.id, Date.now());
  setCookie(c, "sid", token, { httpOnly: true, sameSite: "Lax", path: "/", maxAge: 60 * 60 * 24 * 7 });
  return c.json({ id: row.id, login: row.login, role: row.role });
});
api.post("/logout", (c) => {
  const token = getCookie(c, "sid");
  if (token) run("DELETE FROM sessions WHERE token = ?", token);
  deleteCookie(c, "sid", { path: "/" });
  return c.json({ ok: true });
});
api.get(
  "/about",
  (c) => c.json({
    node: process.version,
    hono: true ? "4.13.9" : version("hono"),
    sqlite: get("SELECT sqlite_version() AS v").v,
    stats: get("SELECT (SELECT COUNT(*) FROM schemes) AS schemes, (SELECT COUNT(*) FROM parts) AS parts, (SELECT COUNT(*) FROM sections) AS sections, (SELECT COUNT(*) FROM users) AS users")
  })
);
api.use(
  "*",
  createMiddleware(async (c, next) => {
    const token = getCookie(c, "sid");
    const user = token ? get("SELECT u.id, u.login, u.role FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?", token) : void 0;
    if (!user) return c.json({ error: "\u0422\u0440\u0435\u0431\u0443\u0435\u0442\u0441\u044F \u0432\u0445\u043E\u0434" }, 401);
    c.set("user", user);
    await next();
  })
);
var adminOnly = createMiddleware(async (c, next) => {
  if (c.get("user").role !== "admin") return c.json({ error: "\u0422\u043E\u043B\u044C\u043A\u043E \u0434\u043B\u044F \u0430\u0434\u043C\u0438\u043D\u0438\u0441\u0442\u0440\u0430\u0442\u043E\u0440\u0430" }, 403);
  await next();
});
api.get("/me", (c) => c.json(c.get("user")));
api.get(
  "/schemes",
  (c) => c.json(
    all(`
      SELECT s.id, s.title, s.model_url, s.image_url, d.title AS device,
             (SELECT COUNT(*) FROM parts p WHERE p.scheme_id = s.id) AS parts
      FROM schemes s JOIN devices d ON d.id = s.device_id
      ORDER BY d.title, s.sort, s.id`)
  )
);
api.get("/schemes/:id", (c) => {
  const scheme = get(
    "SELECT s.id, s.title, s.model_url, s.image_url, d.title AS device FROM schemes s JOIN devices d ON d.id = s.device_id WHERE s.id = ?",
    Number(c.req.param("id"))
  );
  if (!scheme) return c.json({ error: "\u0421\u0445\u0435\u043C\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430" }, 404);
  const parts = all("SELECT id, key, title FROM parts WHERE scheme_id = ? ORDER BY id", Number(scheme.id)).map(toPart);
  return c.json({ ...scheme, parts });
});
api.get(
  "/parts/:id/sections",
  (c) => c.json(all("SELECT id, part_id, title, body, sort FROM sections WHERE part_id = ? ORDER BY sort, id", Number(c.req.param("id"))))
);
var MODEL_EXT = /* @__PURE__ */ new Set([".glb"]);
var IMAGE_EXT = /* @__PURE__ */ new Set([".png", ".jpg", ".jpeg", ".webp", ".svg", ".gif"]);
async function saveUpload(file) {
  const safe = file.name.replace(/[^\w.-]+/g, "_").slice(-60);
  const name = `${Date.now().toString(36)}-${randomBytes2(3).toString("hex")}-${safe}`;
  await writeFile(resolve2(UPLOADS_DIR, name), Buffer.from(await file.arrayBuffer()));
  return `/uploads/${name}`;
}
api.post("/schemes", adminOnly, async (c) => {
  const body = await c.req.parseBody();
  const device = str(body.device);
  const title = str(body.title);
  const model = body.model;
  const image = body.image;
  if (!device || !title) return c.json({ error: "\u0423\u043A\u0430\u0436\u0438\u0442\u0435 \u0438\u0437\u0434\u0435\u043B\u0438\u0435 \u0438 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u0441\u0445\u0435\u043C\u044B" }, 400);
  if (!(model instanceof File) || !MODEL_EXT.has(extname(model.name).toLowerCase())) return c.json({ error: "\u041D\u0443\u0436\u0435\u043D \u0444\u0430\u0439\u043B \u043C\u043E\u0434\u0435\u043B\u0438 .glb" }, 400);
  if (!(image instanceof File) || !IMAGE_EXT.has(extname(image.name).toLowerCase())) return c.json({ error: "\u041D\u0443\u0436\u043D\u0430 \u043A\u0430\u0440\u0442\u0438\u043D\u043A\u0430 \u0441\u0445\u0435\u043C\u044B (png, jpg, svg, webp)" }, 400);
  const modelUrl = await saveUpload(model);
  const imageUrl = await saveUpload(image);
  const id = tx(() => {
    run("INSERT OR IGNORE INTO devices (title) VALUES (?)", device);
    const deviceId = get("SELECT id FROM devices WHERE title = ?", device).id;
    const sort = get("SELECT COALESCE(MAX(sort), -1) + 1 AS n FROM schemes WHERE device_id = ?", deviceId).n;
    return run("INSERT INTO schemes (device_id, title, model_url, image_url, sort) VALUES (?, ?, ?, ?, ?)", deviceId, title, modelUrl, imageUrl, sort).lastInsertRowid;
  });
  return c.json({ id: Number(id) }, 201);
});
api.delete("/schemes/:id", adminOnly, (c) => {
  const id = Number(c.req.param("id"));
  const s = get("SELECT model_url, image_url FROM schemes WHERE id = ?", id);
  if (!s) return c.json({ error: "\u0421\u0445\u0435\u043C\u0430 \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430" }, 404);
  run("DELETE FROM schemes WHERE id = ?", id);
  run("DELETE FROM devices WHERE id NOT IN (SELECT device_id FROM schemes)");
  for (const url2 of [s.model_url, s.image_url]) if (url2.startsWith("/uploads/")) rmSync(resolve2(UPLOADS_DIR, url2.slice("/uploads/".length)), { force: true });
  return c.json({ ok: true });
});
api.post("/schemes/:id/parts", adminOnly, async (c) => {
  const body = await c.req.json().catch(() => ({}));
  const key = str(body.key);
  const title = str(body.title);
  if (!key || !title) return c.json({ error: "\u041D\u0443\u0436\u043D\u044B \u0443\u0437\u0435\u043B \u043C\u043E\u0434\u0435\u043B\u0438 \u0438 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u0435 \u0434\u0435\u0442\u0430\u043B\u0438" }, 400);
  const schemeId = Number(c.req.param("id"));
  if (get("SELECT 1 FROM parts WHERE scheme_id = ? AND key = ?", schemeId, key)) return c.json({ error: "\u042D\u0442\u043E\u0442 \u0443\u0437\u0435\u043B \u0443\u0436\u0435 \u043E\u043F\u0438\u0441\u0430\u043D" }, 409);
  const id = run("INSERT INTO parts (scheme_id, key, title) VALUES (?, ?, ?)", schemeId, key, title).lastInsertRowid;
  run("INSERT INTO sections (part_id, title, body, sort) VALUES (?, ?, ?, 0)", id, "\u0422\u0422\u0425", "| \u041F\u0430\u0440\u0430\u043C\u0435\u0442\u0440 | \u0417\u043D\u0430\u0447\u0435\u043D\u0438\u0435 |\n|---|---|\n| \u2026 | \u2026 |");
  return c.json(partById(id), 201);
});
api.patch("/parts/:id", adminOnly, async (c) => {
  const id = Number(c.req.param("id"));
  const body = await c.req.json().catch(() => ({}));
  if (typeof body.title === "string" && body.title.trim()) run("UPDATE parts SET title = ? WHERE id = ?", body.title.trim(), id);
  const part = partById(id);
  return part ? c.json(part) : c.json({ error: "\u0414\u0435\u0442\u0430\u043B\u044C \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430" }, 404);
});
api.post("/parts/:id/hotspots", adminOnly, async (c) => {
  const id = Number(c.req.param("id"));
  const h = await c.req.json().catch(() => ({}));
  const clamp = (v) => Math.min(1, Math.max(0, Number(v) || 0));
  if (!partById(id)) return c.json({ error: "\u0414\u0435\u0442\u0430\u043B\u044C \u043D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u0430" }, 404);
  run("INSERT INTO hotspots (part_id, x, y, w, h) VALUES (?, ?, ?, ?, ?)", id, clamp(h.x), clamp(h.y), clamp(h.w), clamp(h.h));
  return c.json(partById(id), 201);
});
api.delete("/parts/:id/hotspots", adminOnly, (c) => {
  const id = Number(c.req.param("id"));
  run("DELETE FROM hotspots WHERE part_id = ?", id);
  return c.json(partById(id));
});
api.delete("/parts/:id", adminOnly, (c) => {
  run("DELETE FROM parts WHERE id = ?", Number(c.req.param("id")));
  return c.json({ ok: true });
});
api.post("/parts/:id/sections", adminOnly, async (c) => {
  const partId = Number(c.req.param("id"));
  const body = await c.req.json().catch(() => ({}));
  const sort = get("SELECT COALESCE(MAX(sort), -1) + 1 AS n FROM sections WHERE part_id = ?", partId).n;
  const id = run("INSERT INTO sections (part_id, title, body, sort) VALUES (?, ?, ?, ?)", partId, str(body.title) || "\u041D\u043E\u0432\u044B\u0439 \u0440\u0430\u0437\u0434\u0435\u043B", String(body.body ?? ""), sort).lastInsertRowid;
  return c.json(get("SELECT id, part_id, title, body, sort FROM sections WHERE id = ?", id), 201);
});
api.put("/sections/:id", adminOnly, async (c) => {
  const id = Number(c.req.param("id"));
  const body = await c.req.json().catch(() => ({}));
  run("UPDATE sections SET title = ?, body = ? WHERE id = ?", str(body.title) || "\u0411\u0435\u0437 \u043D\u0430\u0437\u0432\u0430\u043D\u0438\u044F", String(body.body ?? ""), id);
  return c.json(get("SELECT id, part_id, title, body, sort FROM sections WHERE id = ?", id));
});
api.delete("/sections/:id", adminOnly, (c) => {
  run("DELETE FROM sections WHERE id = ?", Number(c.req.param("id")));
  return c.json({ ok: true });
});
var CONTENT = {
  devices: ["id", "title"],
  schemes: ["id", "device_id", "title", "model_url", "image_url", "sort"],
  parts: ["id", "scheme_id", "key", "title"],
  sections: ["id", "part_id", "title", "body", "sort"],
  hotspots: ["id", "part_id", "x", "y", "w", "h"]
};
var uploadName = (url2) => url2.startsWith("/uploads/") ? url2.slice("/uploads/".length) : null;
api.get("/content/export", adminOnly, async (c) => {
  const pack = { format: "uav-content", version: 1, exported_at: (/* @__PURE__ */ new Date()).toISOString() };
  for (const [table, cols] of Object.entries(CONTENT)) pack[table] = all(`SELECT ${cols.join(", ")} FROM ${table}`);
  const files = {};
  for (const s of all("SELECT model_url, image_url FROM schemes"))
    for (const name of [uploadName(s.model_url), uploadName(s.image_url)])
      if (name && !files[name] && existsSync2(resolve2(UPLOADS_DIR, name))) files[name] = (await readFile(resolve2(UPLOADS_DIR, name))).toString("base64");
  pack.files = files;
  const date = (/* @__PURE__ */ new Date()).toISOString().slice(0, 10);
  return c.body(new Uint8Array(gzipSync(JSON.stringify(pack))), 200, {
    "Content-Type": "application/octet-stream",
    "Content-Disposition": `attachment; filename="uav-content-${date}.uavpack"`
  });
});
api.post("/content/import", adminOnly, async (c) => {
  const file = (await c.req.parseBody()).file;
  if (!(file instanceof File)) return c.json({ error: "\u0412\u044B\u0431\u0435\u0440\u0438\u0442\u0435 \u0444\u0430\u0439\u043B .uavpack" }, 400);
  let pack;
  try {
    pack = JSON.parse(gunzipSync(Buffer.from(await file.arrayBuffer())).toString("utf8"));
  } catch {
    return c.json({ error: "\u042D\u0442\u043E \u043D\u0435 \u043F\u0430\u043A\u0435\u0442 \u043A\u043E\u043D\u0442\u0435\u043D\u0442\u0430" }, 400);
  }
  if (pack.format !== "uav-content") return c.json({ error: "\u042D\u0442\u043E \u043D\u0435 \u043F\u0430\u043A\u0435\u0442 \u043A\u043E\u043D\u0442\u0435\u043D\u0442\u0430" }, 400);
  const rows = (t) => Array.isArray(pack[t]) ? pack[t] : [];
  const files = Object.entries(pack.files ?? {});
  if (files.some(([name]) => !/^[\w.-]+$/.test(name))) return c.json({ error: "\u041D\u0435\u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u043E\u0435 \u0438\u043C\u044F \u0444\u0430\u0439\u043B\u0430 \u0432 \u043F\u0430\u043A\u0435\u0442\u0435" }, 400);
  if (rows("schemes").some((s) => !String(s.model_url).startsWith("/") || !String(s.image_url).startsWith("/")))
    return c.json({ error: "\u041D\u0435\u0434\u043E\u043F\u0443\u0441\u0442\u0438\u043C\u0430\u044F \u0441\u0441\u044B\u043B\u043A\u0430 \u043D\u0430 \u0444\u0430\u0439\u043B \u0432 \u043F\u0430\u043A\u0435\u0442\u0435" }, 400);
  for (const [name, b64] of files) await writeFile(resolve2(UPLOADS_DIR, name), Buffer.from(b64, "base64"));
  tx(() => {
    run("DELETE FROM devices");
    for (const [table, cols] of Object.entries(CONTENT))
      for (const row of rows(table))
        run(`INSERT INTO ${table} (${cols.join(", ")}) VALUES (${cols.map(() => "?").join(", ")})`, ...cols.map((k) => row[k] ?? null));
  });
  return c.json({ schemes: rows("schemes").length, parts: rows("parts").length, files: files.length });
});
var app = new Hono2();
app.onError((err, c) => {
  console.error(err);
  return c.json({ error: err.message }, 500);
});
app.route("/api", api);
app.all("/api/*", (c) => c.json({ error: "\u041D\u0435 \u043D\u0430\u0439\u0434\u0435\u043D\u043E" }, 404));
app.use("/uploads/*", serveStatic({ root: "./data" }));
if (existsSync2(DIST_DIR)) {
  app.use("/*", serveStatic({ root: "./dist" }));
  const index = serveStatic({ path: "./dist/index.html" });
  app.get(
    "*",
    createMiddleware(async (c, next) => extname(c.req.path) ? c.notFound() : index(c, next))
  );
}
var port = Number(process.env.PORT ?? 3e3);
var host = process.env.HOST ?? "0.0.0.0";
var url = `http://localhost:${port}`;
var openInBrowser = process.env.UAV_OPEN === "1";
function openBrowser() {
  const [cmd, args] = process.platform === "win32" ? ["cmd", ["/c", "start", "", url]] : process.platform === "darwin" ? ["open", [url]] : ["xdg-open", [url]];
  spawn(cmd, args, { detached: true, stdio: "ignore" }).unref();
}
var server = serve({ fetch: app.fetch, port, hostname: host }, () => {
  const lan = host === "0.0.0.0" ? Object.values(networkInterfaces()).flat().filter((a) => a && a.family === "IPv4" && !a.internal).map((a) => `    http://${a.address}:${port}`) : [];
  console.log(`
  \u0421\u0435\u0440\u0432\u0435\u0440 \u0437\u0430\u043F\u0443\u0449\u0435\u043D:
    ${url}
${lan.join("\n")}`);
  if (!existsSync2(resolve2(DIST_DIR, "index.html"))) console.log("\n  \u0424\u0440\u043E\u043D\u0442\u0435\u043D\u0434 \u043D\u0435 \u0441\u043E\u0431\u0440\u0430\u043D \u2014 \u0432 \u0440\u0435\u0436\u0438\u043C\u0435 \u0440\u0430\u0437\u0440\u0430\u0431\u043E\u0442\u043A\u0438 \u043E\u0442\u043A\u0440\u043E\u0439\u0442\u0435 http://localhost:5173");
  if (openInBrowser) {
    console.log("\n  \u041D\u0435 \u0437\u0430\u043A\u0440\u044B\u0432\u0430\u0439\u0442\u0435 \u044D\u0442\u043E \u043E\u043A\u043D\u043E, \u043F\u043E\u043A\u0430 \u0440\u0430\u0431\u043E\u0442\u0430\u0435\u0442\u0435 \u0441 \u043F\u0440\u043E\u0433\u0440\u0430\u043C\u043C\u043E\u0439.");
    openBrowser();
  }
});
server.on("error", async (err) => {
  if (err.code !== "EADDRINUSE") throw err;
  const ours = await fetch(`${url}/api/about`).then((r) => r.ok, () => false);
  if (ours) {
    console.log(`  \u041F\u0440\u043E\u0433\u0440\u0430\u043C\u043C\u0430 \u0443\u0436\u0435 \u0437\u0430\u043F\u0443\u0449\u0435\u043D\u0430: ${url}`);
    if (openInBrowser) openBrowser();
    process.exit(0);
  }
  console.error(`  \u041F\u043E\u0440\u0442 ${port} \u0437\u0430\u043D\u044F\u0442 \u0434\u0440\u0443\u0433\u043E\u0439 \u043F\u0440\u043E\u0433\u0440\u0430\u043C\u043C\u043E\u0439. \u0417\u0430\u0434\u0430\u0439\u0442\u0435 \u0434\u0440\u0443\u0433\u043E\u0439: set PORT=3001`);
  process.exit(1);
});
