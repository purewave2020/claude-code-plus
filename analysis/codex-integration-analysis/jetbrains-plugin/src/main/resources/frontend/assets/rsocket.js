var commonjsGlobal = typeof globalThis !== "undefined" ? globalThis : typeof window !== "undefined" ? window : typeof global !== "undefined" ? global : typeof self !== "undefined" ? self : {};
function getDefaultExportFromCjs(x) {
  return x && x.__esModule && Object.prototype.hasOwnProperty.call(x, "default") ? x["default"] : x;
}
function getAugmentedNamespace(n) {
  if (n.__esModule) return n;
  var f = n.default;
  if (typeof f == "function") {
    var a = function a2() {
      if (this instanceof a2) {
        return Reflect.construct(f, arguments, this.constructor);
      }
      return f.apply(this, arguments);
    };
    a.prototype = f.prototype;
  } else a = {};
  Object.defineProperty(a, "__esModule", { value: true });
  Object.keys(n).forEach(function(k) {
    var d = Object.getOwnPropertyDescriptor(n, k);
    Object.defineProperty(a, k, d.get ? d : {
      enumerable: true,
      get: function() {
        return n[k];
      }
    });
  });
  return a;
}
var dist$1 = {};
var Codecs = {};
var Frames = {};
(function(exports$1) {
  Object.defineProperty(exports$1, "__esModule", { value: true });
  exports$1.Frame = exports$1.Lengths = exports$1.Flags = exports$1.FrameTypes = void 0;
  var FrameTypes;
  (function(FrameTypes2) {
    FrameTypes2[FrameTypes2["RESERVED"] = 0] = "RESERVED";
    FrameTypes2[FrameTypes2["SETUP"] = 1] = "SETUP";
    FrameTypes2[FrameTypes2["LEASE"] = 2] = "LEASE";
    FrameTypes2[FrameTypes2["KEEPALIVE"] = 3] = "KEEPALIVE";
    FrameTypes2[FrameTypes2["REQUEST_RESPONSE"] = 4] = "REQUEST_RESPONSE";
    FrameTypes2[FrameTypes2["REQUEST_FNF"] = 5] = "REQUEST_FNF";
    FrameTypes2[FrameTypes2["REQUEST_STREAM"] = 6] = "REQUEST_STREAM";
    FrameTypes2[FrameTypes2["REQUEST_CHANNEL"] = 7] = "REQUEST_CHANNEL";
    FrameTypes2[FrameTypes2["REQUEST_N"] = 8] = "REQUEST_N";
    FrameTypes2[FrameTypes2["CANCEL"] = 9] = "CANCEL";
    FrameTypes2[FrameTypes2["PAYLOAD"] = 10] = "PAYLOAD";
    FrameTypes2[FrameTypes2["ERROR"] = 11] = "ERROR";
    FrameTypes2[FrameTypes2["METADATA_PUSH"] = 12] = "METADATA_PUSH";
    FrameTypes2[FrameTypes2["RESUME"] = 13] = "RESUME";
    FrameTypes2[FrameTypes2["RESUME_OK"] = 14] = "RESUME_OK";
    FrameTypes2[FrameTypes2["EXT"] = 63] = "EXT";
  })(FrameTypes = exports$1.FrameTypes || (exports$1.FrameTypes = {}));
  (function(Flags) {
    Flags[Flags["NONE"] = 0] = "NONE";
    Flags[Flags["COMPLETE"] = 64] = "COMPLETE";
    Flags[Flags["FOLLOWS"] = 128] = "FOLLOWS";
    Flags[Flags["IGNORE"] = 512] = "IGNORE";
    Flags[Flags["LEASE"] = 64] = "LEASE";
    Flags[Flags["METADATA"] = 256] = "METADATA";
    Flags[Flags["NEXT"] = 32] = "NEXT";
    Flags[Flags["RESPOND"] = 128] = "RESPOND";
    Flags[Flags["RESUME_ENABLE"] = 128] = "RESUME_ENABLE";
  })(exports$1.Flags || (exports$1.Flags = {}));
  (function(Flags) {
    function hasMetadata(flags) {
      return (flags & Flags.METADATA) === Flags.METADATA;
    }
    Flags.hasMetadata = hasMetadata;
    function hasComplete(flags) {
      return (flags & Flags.COMPLETE) === Flags.COMPLETE;
    }
    Flags.hasComplete = hasComplete;
    function hasNext(flags) {
      return (flags & Flags.NEXT) === Flags.NEXT;
    }
    Flags.hasNext = hasNext;
    function hasFollows(flags) {
      return (flags & Flags.FOLLOWS) === Flags.FOLLOWS;
    }
    Flags.hasFollows = hasFollows;
    function hasIgnore(flags) {
      return (flags & Flags.IGNORE) === Flags.IGNORE;
    }
    Flags.hasIgnore = hasIgnore;
    function hasRespond(flags) {
      return (flags & Flags.RESPOND) === Flags.RESPOND;
    }
    Flags.hasRespond = hasRespond;
    function hasLease(flags) {
      return (flags & Flags.LEASE) === Flags.LEASE;
    }
    Flags.hasLease = hasLease;
    function hasResume(flags) {
      return (flags & Flags.RESUME_ENABLE) === Flags.RESUME_ENABLE;
    }
    Flags.hasResume = hasResume;
  })(exports$1.Flags || (exports$1.Flags = {}));
  (function(Lengths) {
    Lengths[Lengths["FRAME"] = 3] = "FRAME";
    Lengths[Lengths["HEADER"] = 6] = "HEADER";
    Lengths[Lengths["METADATA"] = 3] = "METADATA";
    Lengths[Lengths["REQUEST"] = 3] = "REQUEST";
  })(exports$1.Lengths || (exports$1.Lengths = {}));
  (function(Frame) {
    function isConnection(frame) {
      return frame.streamId === 0;
    }
    Frame.isConnection = isConnection;
    function isRequest(frame) {
      return FrameTypes.REQUEST_RESPONSE <= frame.type && frame.type <= FrameTypes.REQUEST_CHANNEL;
    }
    Frame.isRequest = isRequest;
  })(exports$1.Frame || (exports$1.Frame = {}));
})(Frames);
(function(exports$1) {
  var __generator2 = commonjsGlobal && commonjsGlobal.__generator || function(thisArg, body) {
    var _ = { label: 0, sent: function() {
      if (t[0] & 1) throw t[1];
      return t[1];
    }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() {
      return this;
    }), g;
    function verb(n) {
      return function(v) {
        return step([n, v]);
      };
    }
    function step(op) {
      if (f) throw new TypeError("Generator is already executing.");
      while (_) try {
        if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
        if (y = 0, t) op = [op[0] & 2, t.value];
        switch (op[0]) {
          case 0:
          case 1:
            t = op;
            break;
          case 4:
            _.label++;
            return { value: op[1], done: false };
          case 5:
            _.label++;
            y = op[1];
            op = [0];
            continue;
          case 7:
            op = _.ops.pop();
            _.trys.pop();
            continue;
          default:
            if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) {
              _ = 0;
              continue;
            }
            if (op[0] === 3 && (!t || op[1] > t[0] && op[1] < t[3])) {
              _.label = op[1];
              break;
            }
            if (op[0] === 6 && _.label < t[1]) {
              _.label = t[1];
              t = op;
              break;
            }
            if (t && _.label < t[2]) {
              _.label = t[2];
              _.ops.push(op);
              break;
            }
            if (t[2]) _.ops.pop();
            _.trys.pop();
            continue;
        }
        op = body.call(thisArg, _);
      } catch (e) {
        op = [6, e];
        y = 0;
      } finally {
        f = t = 0;
      }
      if (op[0] & 5) throw op[1];
      return { value: op[0] ? op[1] : void 0, done: true };
    }
  };
  Object.defineProperty(exports$1, "__esModule", { value: true });
  exports$1.Deserializer = exports$1.sizeOfFrame = exports$1.serializeFrame = exports$1.deserializeFrame = exports$1.serializeFrameWithLength = exports$1.deserializeFrames = exports$1.deserializeFrameWithLength = exports$1.writeUInt64BE = exports$1.readUInt64BE = exports$1.writeUInt24BE = exports$1.readUInt24BE = exports$1.MAX_VERSION = exports$1.MAX_TTL = exports$1.MAX_STREAM_ID = exports$1.MAX_RESUME_LENGTH = exports$1.MAX_REQUEST_N = exports$1.MAX_REQUEST_COUNT = exports$1.MAX_MIME_LENGTH = exports$1.MAX_METADATA_LENGTH = exports$1.MAX_LIFETIME = exports$1.MAX_KEEPALIVE = exports$1.MAX_CODE = exports$1.FRAME_TYPE_OFFFSET = exports$1.FLAGS_MASK = void 0;
  var Frames_12 = Frames;
  exports$1.FLAGS_MASK = 1023;
  exports$1.FRAME_TYPE_OFFFSET = 10;
  exports$1.MAX_CODE = 2147483647;
  exports$1.MAX_KEEPALIVE = 2147483647;
  exports$1.MAX_LIFETIME = 2147483647;
  exports$1.MAX_METADATA_LENGTH = 16777215;
  exports$1.MAX_MIME_LENGTH = 255;
  exports$1.MAX_REQUEST_COUNT = 2147483647;
  exports$1.MAX_REQUEST_N = 2147483647;
  exports$1.MAX_RESUME_LENGTH = 65535;
  exports$1.MAX_STREAM_ID = 2147483647;
  exports$1.MAX_TTL = 2147483647;
  exports$1.MAX_VERSION = 65535;
  var BITWISE_OVERFLOW = 4294967296;
  function readUInt24BE(buffer, offset) {
    var val1 = buffer.readUInt8(offset) << 16;
    var val2 = buffer.readUInt8(offset + 1) << 8;
    var val3 = buffer.readUInt8(offset + 2);
    return val1 | val2 | val3;
  }
  exports$1.readUInt24BE = readUInt24BE;
  function writeUInt24BE(buffer, value, offset) {
    offset = buffer.writeUInt8(value >>> 16, offset);
    offset = buffer.writeUInt8(value >>> 8 & 255, offset);
    return buffer.writeUInt8(value & 255, offset);
  }
  exports$1.writeUInt24BE = writeUInt24BE;
  function readUInt64BE(buffer, offset) {
    var high = buffer.readUInt32BE(offset);
    var low = buffer.readUInt32BE(offset + 4);
    return high * BITWISE_OVERFLOW + low;
  }
  exports$1.readUInt64BE = readUInt64BE;
  function writeUInt64BE(buffer, value, offset) {
    var high = value / BITWISE_OVERFLOW | 0;
    var low = value % BITWISE_OVERFLOW;
    offset = buffer.writeUInt32BE(high, offset);
    return buffer.writeUInt32BE(low, offset);
  }
  exports$1.writeUInt64BE = writeUInt64BE;
  var FRAME_HEADER_SIZE = 6;
  var UINT24_SIZE = 3;
  function deserializeFrameWithLength(buffer) {
    var frameLength = readUInt24BE(buffer, 0);
    return deserializeFrame(buffer.slice(UINT24_SIZE, UINT24_SIZE + frameLength));
  }
  exports$1.deserializeFrameWithLength = deserializeFrameWithLength;
  function deserializeFrames(buffer) {
    var offset, frameLength, frameStart, frameEnd, frameBuffer, frame;
    return __generator2(this, function(_a) {
      switch (_a.label) {
        case 0:
          offset = 0;
          _a.label = 1;
        case 1:
          if (!(offset + UINT24_SIZE < buffer.length)) return [3, 3];
          frameLength = readUInt24BE(buffer, offset);
          frameStart = offset + UINT24_SIZE;
          frameEnd = frameStart + frameLength;
          if (frameEnd > buffer.length) {
            return [3, 3];
          }
          frameBuffer = buffer.slice(frameStart, frameEnd);
          frame = deserializeFrame(frameBuffer);
          offset = frameEnd;
          return [4, [frame, offset]];
        case 2:
          _a.sent();
          return [3, 1];
        case 3:
          return [
            2
            /*return*/
          ];
      }
    });
  }
  exports$1.deserializeFrames = deserializeFrames;
  function serializeFrameWithLength(frame) {
    var buffer = serializeFrame(frame);
    var lengthPrefixed = Buffer.allocUnsafe(buffer.length + UINT24_SIZE);
    writeUInt24BE(lengthPrefixed, buffer.length, 0);
    buffer.copy(lengthPrefixed, UINT24_SIZE);
    return lengthPrefixed;
  }
  exports$1.serializeFrameWithLength = serializeFrameWithLength;
  function deserializeFrame(buffer) {
    var offset = 0;
    var streamId = buffer.readInt32BE(offset);
    offset += 4;
    var typeAndFlags = buffer.readUInt16BE(offset);
    offset += 2;
    var type = typeAndFlags >>> exports$1.FRAME_TYPE_OFFFSET;
    var flags = typeAndFlags & exports$1.FLAGS_MASK;
    switch (type) {
      case Frames_12.FrameTypes.SETUP:
        return deserializeSetupFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.PAYLOAD:
        return deserializePayloadFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.ERROR:
        return deserializeErrorFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.KEEPALIVE:
        return deserializeKeepAliveFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.REQUEST_FNF:
        return deserializeRequestFnfFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.REQUEST_RESPONSE:
        return deserializeRequestResponseFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.REQUEST_STREAM:
        return deserializeRequestStreamFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.REQUEST_CHANNEL:
        return deserializeRequestChannelFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.METADATA_PUSH:
        return deserializeMetadataPushFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.REQUEST_N:
        return deserializeRequestNFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.RESUME:
        return deserializeResumeFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.RESUME_OK:
        return deserializeResumeOkFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.CANCEL:
        return deserializeCancelFrame(buffer, streamId, flags);
      case Frames_12.FrameTypes.LEASE:
        return deserializeLeaseFrame(buffer, streamId, flags);
    }
  }
  exports$1.deserializeFrame = deserializeFrame;
  function serializeFrame(frame) {
    switch (frame.type) {
      case Frames_12.FrameTypes.SETUP:
        return serializeSetupFrame(frame);
      case Frames_12.FrameTypes.PAYLOAD:
        return serializePayloadFrame(frame);
      case Frames_12.FrameTypes.ERROR:
        return serializeErrorFrame(frame);
      case Frames_12.FrameTypes.KEEPALIVE:
        return serializeKeepAliveFrame(frame);
      case Frames_12.FrameTypes.REQUEST_FNF:
      case Frames_12.FrameTypes.REQUEST_RESPONSE:
        return serializeRequestFrame(frame);
      case Frames_12.FrameTypes.REQUEST_STREAM:
      case Frames_12.FrameTypes.REQUEST_CHANNEL:
        return serializeRequestManyFrame(frame);
      case Frames_12.FrameTypes.METADATA_PUSH:
        return serializeMetadataPushFrame(frame);
      case Frames_12.FrameTypes.REQUEST_N:
        return serializeRequestNFrame(frame);
      case Frames_12.FrameTypes.RESUME:
        return serializeResumeFrame(frame);
      case Frames_12.FrameTypes.RESUME_OK:
        return serializeResumeOkFrame(frame);
      case Frames_12.FrameTypes.CANCEL:
        return serializeCancelFrame(frame);
      case Frames_12.FrameTypes.LEASE:
        return serializeLeaseFrame(frame);
    }
  }
  exports$1.serializeFrame = serializeFrame;
  function sizeOfFrame(frame) {
    switch (frame.type) {
      case Frames_12.FrameTypes.SETUP:
        return sizeOfSetupFrame(frame);
      case Frames_12.FrameTypes.PAYLOAD:
        return sizeOfPayloadFrame(frame);
      case Frames_12.FrameTypes.ERROR:
        return sizeOfErrorFrame(frame);
      case Frames_12.FrameTypes.KEEPALIVE:
        return sizeOfKeepAliveFrame(frame);
      case Frames_12.FrameTypes.REQUEST_FNF:
      case Frames_12.FrameTypes.REQUEST_RESPONSE:
        return sizeOfRequestFrame(frame);
      case Frames_12.FrameTypes.REQUEST_STREAM:
      case Frames_12.FrameTypes.REQUEST_CHANNEL:
        return sizeOfRequestManyFrame(frame);
      case Frames_12.FrameTypes.METADATA_PUSH:
        return sizeOfMetadataPushFrame(frame);
      case Frames_12.FrameTypes.REQUEST_N:
        return sizeOfRequestNFrame();
      case Frames_12.FrameTypes.RESUME:
        return sizeOfResumeFrame(frame);
      case Frames_12.FrameTypes.RESUME_OK:
        return sizeOfResumeOkFrame();
      case Frames_12.FrameTypes.CANCEL:
        return sizeOfCancelFrame();
      case Frames_12.FrameTypes.LEASE:
        return sizeOfLeaseFrame(frame);
    }
  }
  exports$1.sizeOfFrame = sizeOfFrame;
  var SETUP_FIXED_SIZE = 14;
  var RESUME_TOKEN_LENGTH_SIZE = 2;
  function serializeSetupFrame(frame) {
    var resumeTokenLength = frame.resumeToken != null ? frame.resumeToken.byteLength : 0;
    var metadataMimeTypeLength = frame.metadataMimeType != null ? Buffer.byteLength(frame.metadataMimeType, "ascii") : 0;
    var dataMimeTypeLength = frame.dataMimeType != null ? Buffer.byteLength(frame.dataMimeType, "ascii") : 0;
    var payloadLength = getPayloadLength(frame);
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + SETUP_FIXED_SIZE + //
    (resumeTokenLength ? RESUME_TOKEN_LENGTH_SIZE + resumeTokenLength : 0) + metadataMimeTypeLength + dataMimeTypeLength + payloadLength);
    var offset = writeHeader(frame, buffer);
    offset = buffer.writeUInt16BE(frame.majorVersion, offset);
    offset = buffer.writeUInt16BE(frame.minorVersion, offset);
    offset = buffer.writeUInt32BE(frame.keepAlive, offset);
    offset = buffer.writeUInt32BE(frame.lifetime, offset);
    if (frame.flags & Frames_12.Flags.RESUME_ENABLE) {
      offset = buffer.writeUInt16BE(resumeTokenLength, offset);
      if (frame.resumeToken != null) {
        offset += frame.resumeToken.copy(buffer, offset);
      }
    }
    offset = buffer.writeUInt8(metadataMimeTypeLength, offset);
    if (frame.metadataMimeType != null) {
      offset += buffer.write(frame.metadataMimeType, offset, offset + metadataMimeTypeLength, "ascii");
    }
    offset = buffer.writeUInt8(dataMimeTypeLength, offset);
    if (frame.dataMimeType != null) {
      offset += buffer.write(frame.dataMimeType, offset, offset + dataMimeTypeLength, "ascii");
    }
    writePayload(frame, buffer, offset);
    return buffer;
  }
  function sizeOfSetupFrame(frame) {
    var resumeTokenLength = frame.resumeToken != null ? frame.resumeToken.byteLength : 0;
    var metadataMimeTypeLength = frame.metadataMimeType != null ? Buffer.byteLength(frame.metadataMimeType, "ascii") : 0;
    var dataMimeTypeLength = frame.dataMimeType != null ? Buffer.byteLength(frame.dataMimeType, "ascii") : 0;
    var payloadLength = getPayloadLength(frame);
    return FRAME_HEADER_SIZE + SETUP_FIXED_SIZE + //
    (resumeTokenLength ? RESUME_TOKEN_LENGTH_SIZE + resumeTokenLength : 0) + metadataMimeTypeLength + dataMimeTypeLength + payloadLength;
  }
  function deserializeSetupFrame(buffer, streamId, flags) {
    buffer.length;
    var offset = FRAME_HEADER_SIZE;
    var majorVersion = buffer.readUInt16BE(offset);
    offset += 2;
    var minorVersion = buffer.readUInt16BE(offset);
    offset += 2;
    var keepAlive = buffer.readInt32BE(offset);
    offset += 4;
    var lifetime = buffer.readInt32BE(offset);
    offset += 4;
    var resumeToken = null;
    if (flags & Frames_12.Flags.RESUME_ENABLE) {
      var resumeTokenLength = buffer.readInt16BE(offset);
      offset += 2;
      resumeToken = buffer.slice(offset, offset + resumeTokenLength);
      offset += resumeTokenLength;
    }
    var metadataMimeTypeLength = buffer.readUInt8(offset);
    offset += 1;
    var metadataMimeType = buffer.toString("ascii", offset, offset + metadataMimeTypeLength);
    offset += metadataMimeTypeLength;
    var dataMimeTypeLength = buffer.readUInt8(offset);
    offset += 1;
    var dataMimeType = buffer.toString("ascii", offset, offset + dataMimeTypeLength);
    offset += dataMimeTypeLength;
    var frame = {
      data: null,
      dataMimeType,
      flags,
      keepAlive,
      lifetime,
      majorVersion,
      metadata: null,
      metadataMimeType,
      minorVersion,
      resumeToken,
      // streamId,
      streamId: 0,
      type: Frames_12.FrameTypes.SETUP
    };
    readPayload(buffer, frame, offset);
    return frame;
  }
  var ERROR_FIXED_SIZE = 4;
  function serializeErrorFrame(frame) {
    var messageLength = frame.message != null ? Buffer.byteLength(frame.message, "utf8") : 0;
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + ERROR_FIXED_SIZE + messageLength);
    var offset = writeHeader(frame, buffer);
    offset = buffer.writeUInt32BE(frame.code, offset);
    if (frame.message != null) {
      buffer.write(frame.message, offset, offset + messageLength, "utf8");
    }
    return buffer;
  }
  function sizeOfErrorFrame(frame) {
    var messageLength = frame.message != null ? Buffer.byteLength(frame.message, "utf8") : 0;
    return FRAME_HEADER_SIZE + ERROR_FIXED_SIZE + messageLength;
  }
  function deserializeErrorFrame(buffer, streamId, flags) {
    buffer.length;
    var offset = FRAME_HEADER_SIZE;
    var code = buffer.readInt32BE(offset);
    offset += 4;
    var messageLength = buffer.length - offset;
    var message = "";
    if (messageLength > 0) {
      message = buffer.toString("utf8", offset, offset + messageLength);
      offset += messageLength;
    }
    return {
      code,
      flags,
      message,
      streamId,
      type: Frames_12.FrameTypes.ERROR
    };
  }
  var KEEPALIVE_FIXED_SIZE = 8;
  function serializeKeepAliveFrame(frame) {
    var dataLength = frame.data != null ? frame.data.byteLength : 0;
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + KEEPALIVE_FIXED_SIZE + dataLength);
    var offset = writeHeader(frame, buffer);
    offset = writeUInt64BE(buffer, frame.lastReceivedPosition, offset);
    if (frame.data != null) {
      frame.data.copy(buffer, offset);
    }
    return buffer;
  }
  function sizeOfKeepAliveFrame(frame) {
    var dataLength = frame.data != null ? frame.data.byteLength : 0;
    return FRAME_HEADER_SIZE + KEEPALIVE_FIXED_SIZE + dataLength;
  }
  function deserializeKeepAliveFrame(buffer, streamId, flags) {
    buffer.length;
    var offset = FRAME_HEADER_SIZE;
    var lastReceivedPosition = readUInt64BE(buffer, offset);
    offset += 8;
    var data = null;
    if (offset < buffer.length) {
      data = buffer.slice(offset, buffer.length);
    }
    return {
      data,
      flags,
      lastReceivedPosition,
      // streamId,
      streamId: 0,
      type: Frames_12.FrameTypes.KEEPALIVE
    };
  }
  var LEASE_FIXED_SIZE = 8;
  function serializeLeaseFrame(frame) {
    var metaLength = frame.metadata != null ? frame.metadata.byteLength : 0;
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + LEASE_FIXED_SIZE + metaLength);
    var offset = writeHeader(frame, buffer);
    offset = buffer.writeUInt32BE(frame.ttl, offset);
    offset = buffer.writeUInt32BE(frame.requestCount, offset);
    if (frame.metadata != null) {
      frame.metadata.copy(buffer, offset);
    }
    return buffer;
  }
  function sizeOfLeaseFrame(frame) {
    var metaLength = frame.metadata != null ? frame.metadata.byteLength : 0;
    return FRAME_HEADER_SIZE + LEASE_FIXED_SIZE + metaLength;
  }
  function deserializeLeaseFrame(buffer, streamId, flags) {
    var offset = FRAME_HEADER_SIZE;
    var ttl = buffer.readUInt32BE(offset);
    offset += 4;
    var requestCount = buffer.readUInt32BE(offset);
    offset += 4;
    var metadata = null;
    if (offset < buffer.length) {
      metadata = buffer.slice(offset, buffer.length);
    }
    return {
      flags,
      metadata,
      requestCount,
      // streamId,
      streamId: 0,
      ttl,
      type: Frames_12.FrameTypes.LEASE
    };
  }
  function serializeRequestFrame(frame) {
    var payloadLength = getPayloadLength(frame);
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + payloadLength);
    var offset = writeHeader(frame, buffer);
    writePayload(frame, buffer, offset);
    return buffer;
  }
  function sizeOfRequestFrame(frame) {
    var payloadLength = getPayloadLength(frame);
    return FRAME_HEADER_SIZE + payloadLength;
  }
  function serializeMetadataPushFrame(frame) {
    var metadata = frame.metadata;
    if (metadata != null) {
      var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + metadata.byteLength);
      var offset = writeHeader(frame, buffer);
      metadata.copy(buffer, offset);
      return buffer;
    } else {
      var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE);
      writeHeader(frame, buffer);
      return buffer;
    }
  }
  function sizeOfMetadataPushFrame(frame) {
    return FRAME_HEADER_SIZE + (frame.metadata != null ? frame.metadata.byteLength : 0);
  }
  function deserializeRequestFnfFrame(buffer, streamId, flags) {
    buffer.length;
    var frame = {
      data: null,
      flags,
      // length,
      metadata: null,
      streamId,
      type: Frames_12.FrameTypes.REQUEST_FNF
    };
    readPayload(buffer, frame, FRAME_HEADER_SIZE);
    return frame;
  }
  function deserializeRequestResponseFrame(buffer, streamId, flags) {
    var frame = {
      data: null,
      flags,
      // length,
      metadata: null,
      streamId,
      type: Frames_12.FrameTypes.REQUEST_RESPONSE
    };
    readPayload(buffer, frame, FRAME_HEADER_SIZE);
    return frame;
  }
  function deserializeMetadataPushFrame(buffer, streamId, flags) {
    return {
      flags,
      // length,
      metadata: length === FRAME_HEADER_SIZE ? null : buffer.slice(FRAME_HEADER_SIZE, length),
      // streamId,
      streamId: 0,
      type: Frames_12.FrameTypes.METADATA_PUSH
    };
  }
  var REQUEST_MANY_HEADER = 4;
  function serializeRequestManyFrame(frame) {
    var payloadLength = getPayloadLength(frame);
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + REQUEST_MANY_HEADER + payloadLength);
    var offset = writeHeader(frame, buffer);
    offset = buffer.writeUInt32BE(frame.requestN, offset);
    writePayload(frame, buffer, offset);
    return buffer;
  }
  function sizeOfRequestManyFrame(frame) {
    var payloadLength = getPayloadLength(frame);
    return FRAME_HEADER_SIZE + REQUEST_MANY_HEADER + payloadLength;
  }
  function deserializeRequestStreamFrame(buffer, streamId, flags) {
    buffer.length;
    var offset = FRAME_HEADER_SIZE;
    var requestN = buffer.readInt32BE(offset);
    offset += 4;
    var frame = {
      data: null,
      flags,
      // length,
      metadata: null,
      requestN,
      streamId,
      type: Frames_12.FrameTypes.REQUEST_STREAM
    };
    readPayload(buffer, frame, offset);
    return frame;
  }
  function deserializeRequestChannelFrame(buffer, streamId, flags) {
    buffer.length;
    var offset = FRAME_HEADER_SIZE;
    var requestN = buffer.readInt32BE(offset);
    offset += 4;
    var frame = {
      data: null,
      flags,
      // length,
      metadata: null,
      requestN,
      streamId,
      type: Frames_12.FrameTypes.REQUEST_CHANNEL
    };
    readPayload(buffer, frame, offset);
    return frame;
  }
  var REQUEST_N_HEADER = 4;
  function serializeRequestNFrame(frame) {
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + REQUEST_N_HEADER);
    var offset = writeHeader(frame, buffer);
    buffer.writeUInt32BE(frame.requestN, offset);
    return buffer;
  }
  function sizeOfRequestNFrame(frame) {
    return FRAME_HEADER_SIZE + REQUEST_N_HEADER;
  }
  function deserializeRequestNFrame(buffer, streamId, flags) {
    buffer.length;
    var requestN = buffer.readInt32BE(FRAME_HEADER_SIZE);
    return {
      flags,
      // length,
      requestN,
      streamId,
      type: Frames_12.FrameTypes.REQUEST_N
    };
  }
  function serializeCancelFrame(frame) {
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE);
    writeHeader(frame, buffer);
    return buffer;
  }
  function sizeOfCancelFrame(frame) {
    return FRAME_HEADER_SIZE;
  }
  function deserializeCancelFrame(buffer, streamId, flags) {
    buffer.length;
    return {
      flags,
      // length,
      streamId,
      type: Frames_12.FrameTypes.CANCEL
    };
  }
  function serializePayloadFrame(frame) {
    var payloadLength = getPayloadLength(frame);
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + payloadLength);
    var offset = writeHeader(frame, buffer);
    writePayload(frame, buffer, offset);
    return buffer;
  }
  function sizeOfPayloadFrame(frame) {
    var payloadLength = getPayloadLength(frame);
    return FRAME_HEADER_SIZE + payloadLength;
  }
  function deserializePayloadFrame(buffer, streamId, flags) {
    buffer.length;
    var frame = {
      data: null,
      flags,
      // length,
      metadata: null,
      streamId,
      type: Frames_12.FrameTypes.PAYLOAD
    };
    readPayload(buffer, frame, FRAME_HEADER_SIZE);
    return frame;
  }
  var RESUME_FIXED_SIZE = 22;
  function serializeResumeFrame(frame) {
    var resumeTokenLength = frame.resumeToken.byteLength;
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + RESUME_FIXED_SIZE + resumeTokenLength);
    var offset = writeHeader(frame, buffer);
    offset = buffer.writeUInt16BE(frame.majorVersion, offset);
    offset = buffer.writeUInt16BE(frame.minorVersion, offset);
    offset = buffer.writeUInt16BE(resumeTokenLength, offset);
    offset += frame.resumeToken.copy(buffer, offset);
    offset = writeUInt64BE(buffer, frame.serverPosition, offset);
    writeUInt64BE(buffer, frame.clientPosition, offset);
    return buffer;
  }
  function sizeOfResumeFrame(frame) {
    var resumeTokenLength = frame.resumeToken.byteLength;
    return FRAME_HEADER_SIZE + RESUME_FIXED_SIZE + resumeTokenLength;
  }
  function deserializeResumeFrame(buffer, streamId, flags) {
    buffer.length;
    var offset = FRAME_HEADER_SIZE;
    var majorVersion = buffer.readUInt16BE(offset);
    offset += 2;
    var minorVersion = buffer.readUInt16BE(offset);
    offset += 2;
    var resumeTokenLength = buffer.readInt16BE(offset);
    offset += 2;
    var resumeToken = buffer.slice(offset, offset + resumeTokenLength);
    offset += resumeTokenLength;
    var serverPosition = readUInt64BE(buffer, offset);
    offset += 8;
    var clientPosition = readUInt64BE(buffer, offset);
    offset += 8;
    return {
      clientPosition,
      flags,
      // length,
      majorVersion,
      minorVersion,
      resumeToken,
      serverPosition,
      // streamId,
      streamId: 0,
      type: Frames_12.FrameTypes.RESUME
    };
  }
  var RESUME_OK_FIXED_SIZE = 8;
  function serializeResumeOkFrame(frame) {
    var buffer = Buffer.allocUnsafe(FRAME_HEADER_SIZE + RESUME_OK_FIXED_SIZE);
    var offset = writeHeader(frame, buffer);
    writeUInt64BE(buffer, frame.clientPosition, offset);
    return buffer;
  }
  function sizeOfResumeOkFrame(frame) {
    return FRAME_HEADER_SIZE + RESUME_OK_FIXED_SIZE;
  }
  function deserializeResumeOkFrame(buffer, streamId, flags) {
    buffer.length;
    var clientPosition = readUInt64BE(buffer, FRAME_HEADER_SIZE);
    return {
      clientPosition,
      flags,
      // length,
      // streamId,
      streamId: 0,
      type: Frames_12.FrameTypes.RESUME_OK
    };
  }
  function writeHeader(frame, buffer) {
    var offset = buffer.writeInt32BE(frame.streamId, 0);
    return buffer.writeUInt16BE(frame.type << exports$1.FRAME_TYPE_OFFFSET | frame.flags & exports$1.FLAGS_MASK, offset);
  }
  function getPayloadLength(frame) {
    var payloadLength = 0;
    if (frame.data != null) {
      payloadLength += frame.data.byteLength;
    }
    if (Frames_12.Flags.hasMetadata(frame.flags)) {
      payloadLength += UINT24_SIZE;
      if (frame.metadata != null) {
        payloadLength += frame.metadata.byteLength;
      }
    }
    return payloadLength;
  }
  function writePayload(frame, buffer, offset) {
    if (Frames_12.Flags.hasMetadata(frame.flags)) {
      if (frame.metadata != null) {
        var metaLength = frame.metadata.byteLength;
        offset = writeUInt24BE(buffer, metaLength, offset);
        offset += frame.metadata.copy(buffer, offset);
      } else {
        offset = writeUInt24BE(buffer, 0, offset);
      }
    }
    if (frame.data != null) {
      frame.data.copy(buffer, offset);
    }
  }
  function readPayload(buffer, frame, offset) {
    if (Frames_12.Flags.hasMetadata(frame.flags)) {
      var metaLength = readUInt24BE(buffer, offset);
      offset += UINT24_SIZE;
      if (metaLength > 0) {
        frame.metadata = buffer.slice(offset, offset + metaLength);
        offset += metaLength;
      }
    }
    if (offset < buffer.length) {
      frame.data = buffer.slice(offset, buffer.length);
    }
  }
  var Deserializer = (
    /** @class */
    function() {
      function Deserializer2() {
      }
      Deserializer2.prototype.deserializeFrame = function(buffer) {
        return deserializeFrame(buffer);
      };
      Deserializer2.prototype.deserializeFrameWithLength = function(buffer) {
        return deserializeFrameWithLength(buffer);
      };
      Deserializer2.prototype.deserializeFrames = function(buffer) {
        return deserializeFrames(buffer);
      };
      return Deserializer2;
    }()
  );
  exports$1.Deserializer = Deserializer;
})(Codecs);
var Common = {};
Object.defineProperty(Common, "__esModule", { value: true });
var Deferred$1 = {};
var __values$4 = commonjsGlobal && commonjsGlobal.__values || function(o) {
  var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
  if (m) return m.call(o);
  if (o && typeof o.length === "number") return {
    next: function() {
      if (o && i >= o.length) o = void 0;
      return { value: o && o[i++], done: !o };
    }
  };
  throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
};
Object.defineProperty(Deferred$1, "__esModule", { value: true });
Deferred$1.Deferred = void 0;
var Deferred = (
  /** @class */
  function() {
    function Deferred2() {
      this._done = false;
      this.onCloseCallbacks = [];
    }
    Object.defineProperty(Deferred2.prototype, "done", {
      get: function() {
        return this._done;
      },
      enumerable: false,
      configurable: true
    });
    Deferred2.prototype.close = function(error) {
      var e_1, _a, e_2, _b;
      if (this.done) {
        console.warn("Trying to close for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      this._done = true;
      this._error = error;
      if (error) {
        try {
          for (var _c = __values$4(this.onCloseCallbacks), _d = _c.next(); !_d.done; _d = _c.next()) {
            var callback = _d.value;
            callback(error);
          }
        } catch (e_1_1) {
          e_1 = { error: e_1_1 };
        } finally {
          try {
            if (_d && !_d.done && (_a = _c.return)) _a.call(_c);
          } finally {
            if (e_1) throw e_1.error;
          }
        }
        return;
      }
      try {
        for (var _e = __values$4(this.onCloseCallbacks), _f = _e.next(); !_f.done; _f = _e.next()) {
          var callback = _f.value;
          callback();
        }
      } catch (e_2_1) {
        e_2 = { error: e_2_1 };
      } finally {
        try {
          if (_f && !_f.done && (_b = _e.return)) _b.call(_e);
        } finally {
          if (e_2) throw e_2.error;
        }
      }
    };
    Deferred2.prototype.onClose = function(callback) {
      if (this._done) {
        callback(this._error);
        return;
      }
      this.onCloseCallbacks.push(callback);
    };
    return Deferred2;
  }()
);
Deferred$1.Deferred = Deferred;
var Errors = {};
(function(exports$1) {
  var __extends2 = commonjsGlobal && commonjsGlobal.__extends || /* @__PURE__ */ function() {
    var extendStatics = function(d, b) {
      extendStatics = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(d2, b2) {
        d2.__proto__ = b2;
      } || function(d2, b2) {
        for (var p in b2) if (Object.prototype.hasOwnProperty.call(b2, p)) d2[p] = b2[p];
      };
      return extendStatics(d, b);
    };
    return function(d, b) {
      if (typeof b !== "function" && b !== null)
        throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
      extendStatics(d, b);
      function __() {
        this.constructor = d;
      }
      d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
    };
  }();
  Object.defineProperty(exports$1, "__esModule", { value: true });
  exports$1.ErrorCodes = exports$1.RSocketError = void 0;
  var RSocketError = (
    /** @class */
    function(_super) {
      __extends2(RSocketError2, _super);
      function RSocketError2(code, message) {
        var _this = _super.call(this, message) || this;
        _this.code = code;
        return _this;
      }
      return RSocketError2;
    }(Error)
  );
  exports$1.RSocketError = RSocketError;
  (function(ErrorCodes) {
    ErrorCodes[ErrorCodes["RESERVED"] = 0] = "RESERVED";
    ErrorCodes[ErrorCodes["INVALID_SETUP"] = 1] = "INVALID_SETUP";
    ErrorCodes[ErrorCodes["UNSUPPORTED_SETUP"] = 2] = "UNSUPPORTED_SETUP";
    ErrorCodes[ErrorCodes["REJECTED_SETUP"] = 3] = "REJECTED_SETUP";
    ErrorCodes[ErrorCodes["REJECTED_RESUME"] = 4] = "REJECTED_RESUME";
    ErrorCodes[ErrorCodes["CONNECTION_CLOSE"] = 258] = "CONNECTION_CLOSE";
    ErrorCodes[ErrorCodes["CONNECTION_ERROR"] = 257] = "CONNECTION_ERROR";
    ErrorCodes[ErrorCodes["APPLICATION_ERROR"] = 513] = "APPLICATION_ERROR";
    ErrorCodes[ErrorCodes["REJECTED"] = 514] = "REJECTED";
    ErrorCodes[ErrorCodes["CANCELED"] = 515] = "CANCELED";
    ErrorCodes[ErrorCodes["INVALID"] = 516] = "INVALID";
    ErrorCodes[ErrorCodes["RESERVED_EXTENSION"] = 4294967295] = "RESERVED_EXTENSION";
  })(exports$1.ErrorCodes || (exports$1.ErrorCodes = {}));
})(Errors);
var RSocket = {};
Object.defineProperty(RSocket, "__esModule", { value: true });
var RSocketConnector = {};
var ClientServerMultiplexerDemultiplexer = {};
var hasRequiredClientServerMultiplexerDemultiplexer;
function requireClientServerMultiplexerDemultiplexer() {
  if (hasRequiredClientServerMultiplexerDemultiplexer) return ClientServerMultiplexerDemultiplexer;
  hasRequiredClientServerMultiplexerDemultiplexer = 1;
  (function(exports$1) {
    var __extends2 = commonjsGlobal && commonjsGlobal.__extends || /* @__PURE__ */ function() {
      var extendStatics = function(d, b) {
        extendStatics = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(d2, b2) {
          d2.__proto__ = b2;
        } || function(d2, b2) {
          for (var p in b2) if (Object.prototype.hasOwnProperty.call(b2, p)) d2[p] = b2[p];
        };
        return extendStatics(d, b);
      };
      return function(d, b) {
        if (typeof b !== "function" && b !== null)
          throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
        extendStatics(d, b);
        function __() {
          this.constructor = d;
        }
        d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
      };
    }();
    var __awaiter = commonjsGlobal && commonjsGlobal.__awaiter || function(thisArg, _arguments, P, generator) {
      function adopt(value) {
        return value instanceof P ? value : new P(function(resolve) {
          resolve(value);
        });
      }
      return new (P || (P = Promise))(function(resolve, reject) {
        function fulfilled(value) {
          try {
            step(generator.next(value));
          } catch (e) {
            reject(e);
          }
        }
        function rejected(value) {
          try {
            step(generator["throw"](value));
          } catch (e) {
            reject(e);
          }
        }
        function step(result) {
          result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected);
        }
        step((generator = generator.apply(thisArg, _arguments || [])).next());
      });
    };
    var __generator2 = commonjsGlobal && commonjsGlobal.__generator || function(thisArg, body) {
      var _ = { label: 0, sent: function() {
        if (t[0] & 1) throw t[1];
        return t[1];
      }, trys: [], ops: [] }, f, y, t, g;
      return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() {
        return this;
      }), g;
      function verb(n) {
        return function(v) {
          return step([n, v]);
        };
      }
      function step(op) {
        if (f) throw new TypeError("Generator is already executing.");
        while (_) try {
          if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
          if (y = 0, t) op = [op[0] & 2, t.value];
          switch (op[0]) {
            case 0:
            case 1:
              t = op;
              break;
            case 4:
              _.label++;
              return { value: op[1], done: false };
            case 5:
              _.label++;
              y = op[1];
              op = [0];
              continue;
            case 7:
              op = _.ops.pop();
              _.trys.pop();
              continue;
            default:
              if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) {
                _ = 0;
                continue;
              }
              if (op[0] === 3 && (!t || op[1] > t[0] && op[1] < t[3])) {
                _.label = op[1];
                break;
              }
              if (op[0] === 6 && _.label < t[1]) {
                _.label = t[1];
                t = op;
                break;
              }
              if (t && _.label < t[2]) {
                _.label = t[2];
                _.ops.push(op);
                break;
              }
              if (t[2]) _.ops.pop();
              _.trys.pop();
              continue;
          }
          op = body.call(thisArg, _);
        } catch (e) {
          op = [6, e];
          y = 0;
        } finally {
          f = t = 0;
        }
        if (op[0] & 5) throw op[1];
        return { value: op[0] ? op[1] : void 0, done: true };
      }
    };
    Object.defineProperty(exports$1, "__esModule", { value: true });
    exports$1.ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer = exports$1.ResumableClientServerInputMultiplexerDemultiplexer = exports$1.ClientServerInputMultiplexerDemultiplexer = exports$1.StreamIdGenerator = void 0;
    var _1 = requireDist();
    var Deferred_1 = Deferred$1;
    var Errors_12 = Errors;
    var Frames_12 = Frames;
    (function(StreamIdGenerator) {
      function create(seedId) {
        return new StreamIdGeneratorImpl(seedId);
      }
      StreamIdGenerator.create = create;
      var StreamIdGeneratorImpl = (
        /** @class */
        function() {
          function StreamIdGeneratorImpl2(currentId) {
            this.currentId = currentId;
          }
          StreamIdGeneratorImpl2.prototype.next = function(handler) {
            var nextId = this.currentId + 2;
            if (!handler(nextId)) {
              return;
            }
            this.currentId = nextId;
          };
          return StreamIdGeneratorImpl2;
        }()
      );
    })(exports$1.StreamIdGenerator || (exports$1.StreamIdGenerator = {}));
    var ClientServerInputMultiplexerDemultiplexer = (
      /** @class */
      function(_super) {
        __extends2(ClientServerInputMultiplexerDemultiplexer2, _super);
        function ClientServerInputMultiplexerDemultiplexer2(streamIdSupplier, outbound, closeable) {
          var _this = _super.call(this) || this;
          _this.streamIdSupplier = streamIdSupplier;
          _this.outbound = outbound;
          _this.closeable = closeable;
          _this.registry = {};
          closeable.onClose(_this.close.bind(_this));
          return _this;
        }
        ClientServerInputMultiplexerDemultiplexer2.prototype.handle = function(frame) {
          if (Frames_12.Frame.isConnection(frame)) {
            if (frame.type === _1.FrameTypes.RESERVED) {
              return;
            }
            this.connectionFramesHandler.handle(frame);
          } else if (Frames_12.Frame.isRequest(frame)) {
            if (this.registry[frame.streamId]) {
              return;
            }
            this.requestFramesHandler.handle(frame, this);
          } else {
            var handler = this.registry[frame.streamId];
            if (!handler) {
              return;
            }
            handler.handle(frame);
          }
        };
        ClientServerInputMultiplexerDemultiplexer2.prototype.connectionInbound = function(handler) {
          if (this.connectionFramesHandler) {
            throw new Error("Connection frame handler has already been installed");
          }
          this.connectionFramesHandler = handler;
        };
        ClientServerInputMultiplexerDemultiplexer2.prototype.handleRequestStream = function(handler) {
          if (this.requestFramesHandler) {
            throw new Error("Stream handler has already been installed");
          }
          this.requestFramesHandler = handler;
        };
        ClientServerInputMultiplexerDemultiplexer2.prototype.send = function(frame) {
          this.outbound.send(frame);
        };
        Object.defineProperty(ClientServerInputMultiplexerDemultiplexer2.prototype, "connectionOutbound", {
          get: function() {
            return this;
          },
          enumerable: false,
          configurable: true
        });
        ClientServerInputMultiplexerDemultiplexer2.prototype.createRequestStream = function(streamHandler) {
          var _this = this;
          if (this.done) {
            streamHandler.handleReject(new Error("Already closed"));
            return;
          }
          var registry = this.registry;
          this.streamIdSupplier.next(function(streamId) {
            return streamHandler.handleReady(streamId, _this);
          }, Object.keys(registry));
        };
        ClientServerInputMultiplexerDemultiplexer2.prototype.connect = function(handler) {
          this.registry[handler.streamId] = handler;
        };
        ClientServerInputMultiplexerDemultiplexer2.prototype.disconnect = function(stream) {
          delete this.registry[stream.streamId];
        };
        ClientServerInputMultiplexerDemultiplexer2.prototype.close = function(error) {
          if (this.done) {
            _super.prototype.close.call(this, error);
            return;
          }
          for (var streamId in this.registry) {
            var stream = this.registry[streamId];
            stream.close(new Error("Closed. ".concat(error ? "Original cause [".concat(error, "].") : "")));
          }
          _super.prototype.close.call(this, error);
        };
        return ClientServerInputMultiplexerDemultiplexer2;
      }(Deferred_1.Deferred)
    );
    exports$1.ClientServerInputMultiplexerDemultiplexer = ClientServerInputMultiplexerDemultiplexer;
    var ResumableClientServerInputMultiplexerDemultiplexer = (
      /** @class */
      function(_super) {
        __extends2(ResumableClientServerInputMultiplexerDemultiplexer2, _super);
        function ResumableClientServerInputMultiplexerDemultiplexer2(streamIdSupplier, outbound, closeable, frameStore, token, sessionStoreOrReconnector, sessionTimeout) {
          var _this = _super.call(this, streamIdSupplier, outbound, new Deferred_1.Deferred()) || this;
          _this.frameStore = frameStore;
          _this.token = token;
          _this.sessionTimeout = sessionTimeout;
          if (sessionStoreOrReconnector instanceof Function) {
            _this.reconnector = sessionStoreOrReconnector;
          } else {
            _this.sessionStore = sessionStoreOrReconnector;
          }
          closeable.onClose(_this.handleConnectionClose.bind(_this));
          return _this;
        }
        ResumableClientServerInputMultiplexerDemultiplexer2.prototype.send = function(frame) {
          if (Frames_12.Frame.isConnection(frame)) {
            if (frame.type === _1.FrameTypes.KEEPALIVE) {
              frame.lastReceivedPosition = this.frameStore.lastReceivedFramePosition;
            } else if (frame.type === _1.FrameTypes.ERROR) {
              this.outbound.send(frame);
              if (this.sessionStore) {
                delete this.sessionStore[this.token];
              }
              _super.prototype.close.call(this, new Errors_12.RSocketError(frame.code, frame.message));
              return;
            }
          } else {
            this.frameStore.store(frame);
          }
          this.outbound.send(frame);
        };
        ResumableClientServerInputMultiplexerDemultiplexer2.prototype.handle = function(frame) {
          if (Frames_12.Frame.isConnection(frame)) {
            if (frame.type === _1.FrameTypes.KEEPALIVE) {
              try {
                this.frameStore.dropTo(frame.lastReceivedPosition);
              } catch (re) {
                this.outbound.send({
                  type: _1.FrameTypes.ERROR,
                  streamId: 0,
                  flags: _1.Flags.NONE,
                  code: re.code,
                  message: re.message
                });
                this.close(re);
              }
            } else if (frame.type === _1.FrameTypes.ERROR) {
              _super.prototype.handle.call(this, frame);
              if (this.sessionStore) {
                delete this.sessionStore[this.token];
              }
              _super.prototype.close.call(this, new Errors_12.RSocketError(frame.code, frame.message));
              return;
            }
          } else {
            this.frameStore.record(frame);
          }
          _super.prototype.handle.call(this, frame);
        };
        ResumableClientServerInputMultiplexerDemultiplexer2.prototype.resume = function(frame, outbound, closeable) {
          this.outbound = outbound;
          switch (frame.type) {
            case _1.FrameTypes.RESUME: {
              clearTimeout(this.timeoutId);
              if (this.frameStore.lastReceivedFramePosition < frame.clientPosition) {
                var e = new Errors_12.RSocketError(_1.ErrorCodes.REJECTED_RESUME, "Impossible to resume since first available client frame position is greater than last received server frame position");
                this.outbound.send({
                  type: _1.FrameTypes.ERROR,
                  streamId: 0,
                  flags: _1.Flags.NONE,
                  code: e.code,
                  message: e.message
                });
                this.close(e);
                return;
              }
              try {
                this.frameStore.dropTo(frame.serverPosition);
              } catch (re) {
                this.outbound.send({
                  type: _1.FrameTypes.ERROR,
                  streamId: 0,
                  flags: _1.Flags.NONE,
                  code: re.code,
                  message: re.message
                });
                this.close(re);
                return;
              }
              this.outbound.send({
                type: _1.FrameTypes.RESUME_OK,
                streamId: 0,
                flags: _1.Flags.NONE,
                clientPosition: this.frameStore.lastReceivedFramePosition
              });
              break;
            }
            case _1.FrameTypes.RESUME_OK: {
              try {
                this.frameStore.dropTo(frame.clientPosition);
              } catch (re) {
                this.outbound.send({
                  type: _1.FrameTypes.ERROR,
                  streamId: 0,
                  flags: _1.Flags.NONE,
                  code: re.code,
                  message: re.message
                });
                this.close(re);
              }
              break;
            }
          }
          this.frameStore.drain(this.outbound.send.bind(this.outbound));
          closeable.onClose(this.handleConnectionClose.bind(this));
          this.connectionFramesHandler.resume();
        };
        ResumableClientServerInputMultiplexerDemultiplexer2.prototype.handleConnectionClose = function(_error) {
          return __awaiter(this, void 0, void 0, function() {
            var e_1;
            return __generator2(this, function(_a) {
              switch (_a.label) {
                case 0:
                  this.connectionFramesHandler.pause();
                  if (!this.reconnector) return [3, 5];
                  _a.label = 1;
                case 1:
                  _a.trys.push([1, 3, , 4]);
                  return [4, this.reconnector(this, this.frameStore)];
                case 2:
                  _a.sent();
                  return [3, 4];
                case 3:
                  e_1 = _a.sent();
                  this.close(e_1);
                  return [3, 4];
                case 4:
                  return [3, 6];
                case 5:
                  this.timeoutId = setTimeout(this.close.bind(this), this.sessionTimeout);
                  _a.label = 6;
                case 6:
                  return [
                    2
                    /*return*/
                  ];
              }
            });
          });
        };
        return ResumableClientServerInputMultiplexerDemultiplexer2;
      }(ClientServerInputMultiplexerDemultiplexer)
    );
    exports$1.ResumableClientServerInputMultiplexerDemultiplexer = ResumableClientServerInputMultiplexerDemultiplexer;
    var ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer = (
      /** @class */
      function() {
        function ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2(outbound, closeable, delegate) {
          this.outbound = outbound;
          this.closeable = closeable;
          this.delegate = delegate;
          this.resumed = false;
        }
        ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2.prototype.close = function() {
          this.delegate.close();
        };
        ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2.prototype.onClose = function(callback) {
          this.delegate.onClose(callback);
        };
        Object.defineProperty(ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2.prototype, "connectionOutbound", {
          get: function() {
            return this.delegate.connectionOutbound;
          },
          enumerable: false,
          configurable: true
        });
        ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2.prototype.createRequestStream = function(streamHandler) {
          this.delegate.createRequestStream(streamHandler);
        };
        ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2.prototype.connectionInbound = function(handler) {
          this.delegate.connectionInbound(handler);
        };
        ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2.prototype.handleRequestStream = function(handler) {
          this.delegate.handleRequestStream(handler);
        };
        ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2.prototype.handle = function(frame) {
          var _this = this;
          if (!this.resumed) {
            if (frame.type === _1.FrameTypes.RESUME_OK) {
              this.resumed = true;
              this.delegate.resume(frame, this.outbound, this.closeable);
              return;
            } else {
              this.outbound.send({
                type: _1.FrameTypes.ERROR,
                streamId: 0,
                code: _1.ErrorCodes.CONNECTION_ERROR,
                message: "Incomplete RESUME handshake. Unexpected frame ".concat(frame.type, " received"),
                flags: _1.Flags.NONE
              });
              this.closeable.close();
              this.closeable.onClose(function() {
                return _this.delegate.close(new Errors_12.RSocketError(_1.ErrorCodes.CONNECTION_ERROR, "Incomplete RESUME handshake. Unexpected frame ".concat(frame.type, " received")));
              });
            }
            return;
          }
          this.delegate.handle(frame);
        };
        return ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer2;
      }()
    );
    exports$1.ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer = ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer;
  })(ClientServerMultiplexerDemultiplexer);
  return ClientServerMultiplexerDemultiplexer;
}
var RSocketSupport = {};
var RequestChannelStream = {};
var Fragmenter = {};
var __generator = commonjsGlobal && commonjsGlobal.__generator || function(thisArg, body) {
  var _ = { label: 0, sent: function() {
    if (t[0] & 1) throw t[1];
    return t[1];
  }, trys: [], ops: [] }, f, y, t, g;
  return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() {
    return this;
  }), g;
  function verb(n) {
    return function(v) {
      return step([n, v]);
    };
  }
  function step(op) {
    if (f) throw new TypeError("Generator is already executing.");
    while (_) try {
      if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
      if (y = 0, t) op = [op[0] & 2, t.value];
      switch (op[0]) {
        case 0:
        case 1:
          t = op;
          break;
        case 4:
          _.label++;
          return { value: op[1], done: false };
        case 5:
          _.label++;
          y = op[1];
          op = [0];
          continue;
        case 7:
          op = _.ops.pop();
          _.trys.pop();
          continue;
        default:
          if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) {
            _ = 0;
            continue;
          }
          if (op[0] === 3 && (!t || op[1] > t[0] && op[1] < t[3])) {
            _.label = op[1];
            break;
          }
          if (op[0] === 6 && _.label < t[1]) {
            _.label = t[1];
            t = op;
            break;
          }
          if (t && _.label < t[2]) {
            _.label = t[2];
            _.ops.push(op);
            break;
          }
          if (t[2]) _.ops.pop();
          _.trys.pop();
          continue;
      }
      op = body.call(thisArg, _);
    } catch (e) {
      op = [6, e];
      y = 0;
    } finally {
      f = t = 0;
    }
    if (op[0] & 5) throw op[1];
    return { value: op[0] ? op[1] : void 0, done: true };
  }
};
Object.defineProperty(Fragmenter, "__esModule", { value: true });
Fragmenter.fragmentWithRequestN = Fragmenter.fragment = Fragmenter.isFragmentable = void 0;
var Frames_1$5 = Frames;
function isFragmentable(payload, fragmentSize, frameType) {
  if (fragmentSize === 0) {
    return false;
  }
  return payload.data.byteLength + (payload.metadata ? payload.metadata.byteLength + Frames_1$5.Lengths.METADATA : 0) + (frameType == Frames_1$5.FrameTypes.REQUEST_STREAM || frameType == Frames_1$5.FrameTypes.REQUEST_CHANNEL ? Frames_1$5.Lengths.REQUEST : 0) > fragmentSize;
}
Fragmenter.isFragmentable = isFragmentable;
function fragment(streamId, payload, fragmentSize, frameType, isComplete) {
  var dataLength, firstFrame, remaining, metadata, metadataLength, metadataPosition, nextMetadataPosition, nextMetadataPosition, dataPosition, data, nextDataPosition, nextDataPosition;
  var _a, _b;
  if (isComplete === void 0) {
    isComplete = false;
  }
  return __generator(this, function(_c) {
    switch (_c.label) {
      case 0:
        dataLength = (_b = (_a = payload.data) === null || _a === void 0 ? void 0 : _a.byteLength) !== null && _b !== void 0 ? _b : 0;
        firstFrame = frameType !== Frames_1$5.FrameTypes.PAYLOAD;
        remaining = fragmentSize;
        if (!payload.metadata) return [3, 6];
        metadataLength = payload.metadata.byteLength;
        if (!(metadataLength === 0)) return [3, 1];
        remaining -= Frames_1$5.Lengths.METADATA;
        metadata = Buffer.allocUnsafe(0);
        return [3, 6];
      case 1:
        metadataPosition = 0;
        if (!firstFrame) return [3, 3];
        remaining -= Frames_1$5.Lengths.METADATA;
        nextMetadataPosition = Math.min(metadataLength, metadataPosition + remaining);
        metadata = payload.metadata.slice(metadataPosition, nextMetadataPosition);
        remaining -= metadata.byteLength;
        metadataPosition = nextMetadataPosition;
        if (!(remaining === 0)) return [3, 3];
        firstFrame = false;
        return [4, {
          type: frameType,
          flags: Frames_1$5.Flags.FOLLOWS | Frames_1$5.Flags.METADATA,
          data: void 0,
          metadata,
          streamId
        }];
      case 2:
        _c.sent();
        metadata = void 0;
        remaining = fragmentSize;
        _c.label = 3;
      case 3:
        if (!(metadataPosition < metadataLength)) return [3, 6];
        remaining -= Frames_1$5.Lengths.METADATA;
        nextMetadataPosition = Math.min(metadataLength, metadataPosition + remaining);
        metadata = payload.metadata.slice(metadataPosition, nextMetadataPosition);
        remaining -= metadata.byteLength;
        metadataPosition = nextMetadataPosition;
        if (!(remaining === 0 || dataLength === 0)) return [3, 5];
        return [4, {
          type: Frames_1$5.FrameTypes.PAYLOAD,
          flags: Frames_1$5.Flags.NEXT | Frames_1$5.Flags.METADATA | (metadataPosition === metadataLength && isComplete && dataLength === 0 ? Frames_1$5.Flags.COMPLETE : Frames_1$5.Flags.FOLLOWS),
          data: void 0,
          metadata,
          streamId
        }];
      case 4:
        _c.sent();
        metadata = void 0;
        remaining = fragmentSize;
        _c.label = 5;
      case 5:
        return [3, 3];
      case 6:
        dataPosition = 0;
        if (!firstFrame) return [3, 8];
        nextDataPosition = Math.min(dataLength, dataPosition + remaining);
        data = payload.data.slice(dataPosition, nextDataPosition);
        remaining -= data.byteLength;
        dataPosition = nextDataPosition;
        return [4, {
          type: frameType,
          flags: Frames_1$5.Flags.FOLLOWS | (metadata ? Frames_1$5.Flags.METADATA : Frames_1$5.Flags.NONE),
          data,
          metadata,
          streamId
        }];
      case 7:
        _c.sent();
        metadata = void 0;
        data = void 0;
        remaining = fragmentSize;
        _c.label = 8;
      case 8:
        if (!(dataPosition < dataLength)) return [3, 10];
        nextDataPosition = Math.min(dataLength, dataPosition + remaining);
        data = payload.data.slice(dataPosition, nextDataPosition);
        remaining -= data.byteLength;
        dataPosition = nextDataPosition;
        return [4, {
          type: Frames_1$5.FrameTypes.PAYLOAD,
          flags: dataPosition === dataLength ? (isComplete ? Frames_1$5.Flags.COMPLETE : Frames_1$5.Flags.NONE) | Frames_1$5.Flags.NEXT | (metadata ? Frames_1$5.Flags.METADATA : 0) : Frames_1$5.Flags.FOLLOWS | Frames_1$5.Flags.NEXT | (metadata ? Frames_1$5.Flags.METADATA : 0),
          data,
          metadata,
          streamId
        }];
      case 9:
        _c.sent();
        metadata = void 0;
        data = void 0;
        remaining = fragmentSize;
        return [3, 8];
      case 10:
        return [
          2
          /*return*/
        ];
    }
  });
}
Fragmenter.fragment = fragment;
function fragmentWithRequestN(streamId, payload, fragmentSize, frameType, requestN, isComplete) {
  var dataLength, firstFrame, remaining, metadata, metadataLength, metadataPosition, nextMetadataPosition, nextMetadataPosition, dataPosition, data, nextDataPosition, nextDataPosition;
  var _a, _b;
  if (isComplete === void 0) {
    isComplete = false;
  }
  return __generator(this, function(_c) {
    switch (_c.label) {
      case 0:
        dataLength = (_b = (_a = payload.data) === null || _a === void 0 ? void 0 : _a.byteLength) !== null && _b !== void 0 ? _b : 0;
        firstFrame = true;
        remaining = fragmentSize;
        if (!payload.metadata) return [3, 6];
        metadataLength = payload.metadata.byteLength;
        if (!(metadataLength === 0)) return [3, 1];
        remaining -= Frames_1$5.Lengths.METADATA;
        metadata = Buffer.allocUnsafe(0);
        return [3, 6];
      case 1:
        metadataPosition = 0;
        if (!firstFrame) return [3, 3];
        remaining -= Frames_1$5.Lengths.METADATA + Frames_1$5.Lengths.REQUEST;
        nextMetadataPosition = Math.min(metadataLength, metadataPosition + remaining);
        metadata = payload.metadata.slice(metadataPosition, nextMetadataPosition);
        remaining -= metadata.byteLength;
        metadataPosition = nextMetadataPosition;
        if (!(remaining === 0)) return [3, 3];
        firstFrame = false;
        return [4, {
          type: frameType,
          flags: Frames_1$5.Flags.FOLLOWS | Frames_1$5.Flags.METADATA,
          data: void 0,
          requestN,
          metadata,
          streamId
        }];
      case 2:
        _c.sent();
        metadata = void 0;
        remaining = fragmentSize;
        _c.label = 3;
      case 3:
        if (!(metadataPosition < metadataLength)) return [3, 6];
        remaining -= Frames_1$5.Lengths.METADATA;
        nextMetadataPosition = Math.min(metadataLength, metadataPosition + remaining);
        metadata = payload.metadata.slice(metadataPosition, nextMetadataPosition);
        remaining -= metadata.byteLength;
        metadataPosition = nextMetadataPosition;
        if (!(remaining === 0 || dataLength === 0)) return [3, 5];
        return [4, {
          type: Frames_1$5.FrameTypes.PAYLOAD,
          flags: Frames_1$5.Flags.NEXT | Frames_1$5.Flags.METADATA | (metadataPosition === metadataLength && isComplete && dataLength === 0 ? Frames_1$5.Flags.COMPLETE : Frames_1$5.Flags.FOLLOWS),
          data: void 0,
          metadata,
          streamId
        }];
      case 4:
        _c.sent();
        metadata = void 0;
        remaining = fragmentSize;
        _c.label = 5;
      case 5:
        return [3, 3];
      case 6:
        dataPosition = 0;
        if (!firstFrame) return [3, 8];
        remaining -= Frames_1$5.Lengths.REQUEST;
        nextDataPosition = Math.min(dataLength, dataPosition + remaining);
        data = payload.data.slice(dataPosition, nextDataPosition);
        remaining -= data.byteLength;
        dataPosition = nextDataPosition;
        return [4, {
          type: frameType,
          flags: Frames_1$5.Flags.FOLLOWS | (metadata ? Frames_1$5.Flags.METADATA : Frames_1$5.Flags.NONE),
          data,
          requestN,
          metadata,
          streamId
        }];
      case 7:
        _c.sent();
        metadata = void 0;
        data = void 0;
        remaining = fragmentSize;
        _c.label = 8;
      case 8:
        if (!(dataPosition < dataLength)) return [3, 10];
        nextDataPosition = Math.min(dataLength, dataPosition + remaining);
        data = payload.data.slice(dataPosition, nextDataPosition);
        remaining -= data.byteLength;
        dataPosition = nextDataPosition;
        return [4, {
          type: Frames_1$5.FrameTypes.PAYLOAD,
          flags: dataPosition === dataLength ? (isComplete ? Frames_1$5.Flags.COMPLETE : Frames_1$5.Flags.NONE) | Frames_1$5.Flags.NEXT | (metadata ? Frames_1$5.Flags.METADATA : 0) : Frames_1$5.Flags.FOLLOWS | Frames_1$5.Flags.NEXT | (metadata ? Frames_1$5.Flags.METADATA : 0),
          data,
          metadata,
          streamId
        }];
      case 9:
        _c.sent();
        metadata = void 0;
        data = void 0;
        remaining = fragmentSize;
        return [3, 8];
      case 10:
        return [
          2
          /*return*/
        ];
    }
  });
}
Fragmenter.fragmentWithRequestN = fragmentWithRequestN;
var Reassembler$4 = {};
Object.defineProperty(Reassembler$4, "__esModule", { value: true });
Reassembler$4.cancel = Reassembler$4.reassemble = Reassembler$4.add = void 0;
function add(holder, dataFragment, metadataFragment) {
  if (!holder.hasFragments) {
    holder.hasFragments = true;
    holder.data = dataFragment;
    if (metadataFragment) {
      holder.metadata = metadataFragment;
    }
    return true;
  }
  holder.data = holder.data ? Buffer.concat([holder.data, dataFragment]) : dataFragment;
  if (holder.metadata && metadataFragment) {
    holder.metadata = Buffer.concat([holder.metadata, metadataFragment]);
  }
  return true;
}
Reassembler$4.add = add;
function reassemble(holder, dataFragment, metadataFragment) {
  holder.hasFragments = false;
  var data = holder.data ? Buffer.concat([holder.data, dataFragment]) : dataFragment;
  holder.data = void 0;
  if (holder.metadata) {
    var metadata = metadataFragment ? Buffer.concat([holder.metadata, metadataFragment]) : holder.metadata;
    holder.metadata = void 0;
    return {
      data,
      metadata
    };
  }
  return {
    data
  };
}
Reassembler$4.reassemble = reassemble;
function cancel(holder) {
  holder.hasFragments = false;
  holder.data = void 0;
  holder.metadata = void 0;
}
Reassembler$4.cancel = cancel;
var __createBinding$3 = commonjsGlobal && commonjsGlobal.__createBinding || (Object.create ? function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  Object.defineProperty(o, k2, { enumerable: true, get: function() {
    return m[k];
  } });
} : function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  o[k2] = m[k];
});
var __setModuleDefault$3 = commonjsGlobal && commonjsGlobal.__setModuleDefault || (Object.create ? function(o, v) {
  Object.defineProperty(o, "default", { enumerable: true, value: v });
} : function(o, v) {
  o["default"] = v;
});
var __importStar$3 = commonjsGlobal && commonjsGlobal.__importStar || function(mod) {
  if (mod && mod.__esModule) return mod;
  var result = {};
  if (mod != null) {
    for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding$3(result, mod, k);
  }
  __setModuleDefault$3(result, mod);
  return result;
};
var __values$3 = commonjsGlobal && commonjsGlobal.__values || function(o) {
  var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
  if (m) return m.call(o);
  if (o && typeof o.length === "number") return {
    next: function() {
      if (o && i >= o.length) o = void 0;
      return { value: o && o[i++], done: !o };
    }
  };
  throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
};
Object.defineProperty(RequestChannelStream, "__esModule", { value: true });
RequestChannelStream.RequestChannelResponderStream = RequestChannelStream.RequestChannelRequesterStream = void 0;
var Errors_1$4 = Errors;
var Fragmenter_1$3 = Fragmenter;
var Frames_1$4 = Frames;
var Reassembler$3 = __importStar$3(Reassembler$4);
var RequestChannelRequesterStream = (
  /** @class */
  function() {
    function RequestChannelRequesterStream2(payload, isComplete, receiver, fragmentSize, initialRequestN, leaseManager) {
      this.payload = payload;
      this.isComplete = isComplete;
      this.receiver = receiver;
      this.fragmentSize = fragmentSize;
      this.initialRequestN = initialRequestN;
      this.leaseManager = leaseManager;
      this.streamType = Frames_1$4.FrameTypes.REQUEST_CHANNEL;
    }
    RequestChannelRequesterStream2.prototype.handleReady = function(streamId, stream) {
      var e_1, _a;
      if (this.outboundDone) {
        return false;
      }
      this.streamId = streamId;
      this.stream = stream;
      stream.connect(this);
      var isCompleted = this.isComplete;
      if (isCompleted) {
        this.outboundDone = isCompleted;
      }
      if ((0, Fragmenter_1$3.isFragmentable)(this.payload, this.fragmentSize, Frames_1$4.FrameTypes.REQUEST_CHANNEL)) {
        try {
          for (var _b = __values$3((0, Fragmenter_1$3.fragmentWithRequestN)(streamId, this.payload, this.fragmentSize, Frames_1$4.FrameTypes.REQUEST_CHANNEL, this.initialRequestN, isCompleted)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            this.stream.send(frame);
          }
        } catch (e_1_1) {
          e_1 = { error: e_1_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_1) throw e_1.error;
          }
        }
      } else {
        this.stream.send({
          type: Frames_1$4.FrameTypes.REQUEST_CHANNEL,
          data: this.payload.data,
          metadata: this.payload.metadata,
          requestN: this.initialRequestN,
          flags: (this.payload.metadata !== void 0 ? Frames_1$4.Flags.METADATA : Frames_1$4.Flags.NONE) | (isCompleted ? Frames_1$4.Flags.COMPLETE : Frames_1$4.Flags.NONE),
          streamId
        });
      }
      if (this.hasExtension) {
        this.stream.send({
          type: Frames_1$4.FrameTypes.EXT,
          streamId,
          extendedContent: this.extendedContent,
          extendedType: this.extendedType,
          flags: this.flags
        });
      }
      return true;
    };
    RequestChannelRequesterStream2.prototype.handleReject = function(error) {
      if (this.inboundDone) {
        return;
      }
      this.inboundDone = true;
      this.outboundDone = true;
      this.receiver.onError(error);
    };
    RequestChannelRequesterStream2.prototype.handle = function(frame) {
      var errorMessage;
      var frameType = frame.type;
      switch (frameType) {
        case Frames_1$4.FrameTypes.PAYLOAD: {
          var hasComplete = Frames_1$4.Flags.hasComplete(frame.flags);
          var hasNext = Frames_1$4.Flags.hasNext(frame.flags);
          if (hasComplete || !Frames_1$4.Flags.hasFollows(frame.flags)) {
            if (hasComplete) {
              this.inboundDone = true;
              if (this.outboundDone) {
                this.stream.disconnect(this);
              }
              if (!hasNext) {
                this.receiver.onComplete();
                return;
              }
            }
            var payload = this.hasFragments ? Reassembler$3.reassemble(this, frame.data, frame.metadata) : {
              data: frame.data,
              metadata: frame.metadata
            };
            this.receiver.onNext(payload, hasComplete);
            return;
          }
          if (Reassembler$3.add(this, frame.data, frame.metadata)) {
            return;
          }
          errorMessage = "Unexpected frame size";
          break;
        }
        case Frames_1$4.FrameTypes.CANCEL: {
          if (this.outboundDone) {
            return;
          }
          this.outboundDone = true;
          if (this.inboundDone) {
            this.stream.disconnect(this);
          }
          this.receiver.cancel();
          return;
        }
        case Frames_1$4.FrameTypes.REQUEST_N: {
          if (this.outboundDone) {
            return;
          }
          if (this.hasFragments) {
            errorMessage = "Unexpected frame type [".concat(frameType, "] during reassembly");
            break;
          }
          this.receiver.request(frame.requestN);
          return;
        }
        case Frames_1$4.FrameTypes.ERROR: {
          var outboundDone = this.outboundDone;
          this.inboundDone = true;
          this.outboundDone = true;
          this.stream.disconnect(this);
          Reassembler$3.cancel(this);
          if (!outboundDone) {
            this.receiver.cancel();
          }
          this.receiver.onError(new Errors_1$4.RSocketError(frame.code, frame.message));
          return;
        }
        case Frames_1$4.FrameTypes.EXT:
          this.receiver.onExtension(frame.extendedType, frame.extendedContent, Frames_1$4.Flags.hasIgnore(frame.flags));
          return;
        default: {
          errorMessage = "Unexpected frame type [".concat(frameType, "]");
        }
      }
      this.close(new Errors_1$4.RSocketError(Errors_1$4.ErrorCodes.CANCELED, errorMessage));
      this.stream.send({
        type: Frames_1$4.FrameTypes.CANCEL,
        streamId: this.streamId,
        flags: Frames_1$4.Flags.NONE
      });
      this.stream.disconnect(this);
    };
    RequestChannelRequesterStream2.prototype.request = function(n) {
      if (this.inboundDone) {
        return;
      }
      if (!this.streamId) {
        this.initialRequestN += n;
        return;
      }
      this.stream.send({
        type: Frames_1$4.FrameTypes.REQUEST_N,
        flags: Frames_1$4.Flags.NONE,
        requestN: n,
        streamId: this.streamId
      });
    };
    RequestChannelRequesterStream2.prototype.cancel = function() {
      var _a;
      var inboundDone = this.inboundDone;
      var outboundDone = this.outboundDone;
      if (inboundDone && outboundDone) {
        return;
      }
      this.inboundDone = true;
      this.outboundDone = true;
      if (!outboundDone) {
        this.receiver.cancel();
      }
      if (!this.streamId) {
        (_a = this.leaseManager) === null || _a === void 0 ? void 0 : _a.cancelRequest(this);
        return;
      }
      this.stream.send({
        type: inboundDone ? Frames_1$4.FrameTypes.ERROR : Frames_1$4.FrameTypes.CANCEL,
        flags: Frames_1$4.Flags.NONE,
        streamId: this.streamId,
        code: Errors_1$4.ErrorCodes.CANCELED,
        message: "Cancelled"
      });
      this.stream.disconnect(this);
      Reassembler$3.cancel(this);
    };
    RequestChannelRequesterStream2.prototype.onNext = function(payload, isComplete) {
      var e_2, _a;
      if (this.outboundDone) {
        return;
      }
      if (isComplete) {
        this.outboundDone = true;
        if (this.inboundDone) {
          this.stream.disconnect(this);
        }
      }
      if ((0, Fragmenter_1$3.isFragmentable)(payload, this.fragmentSize, Frames_1$4.FrameTypes.PAYLOAD)) {
        try {
          for (var _b = __values$3((0, Fragmenter_1$3.fragment)(this.streamId, payload, this.fragmentSize, Frames_1$4.FrameTypes.PAYLOAD, isComplete)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            this.stream.send(frame);
          }
        } catch (e_2_1) {
          e_2 = { error: e_2_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_2) throw e_2.error;
          }
        }
      } else {
        this.stream.send({
          type: Frames_1$4.FrameTypes.PAYLOAD,
          streamId: this.streamId,
          flags: Frames_1$4.Flags.NEXT | (payload.metadata ? Frames_1$4.Flags.METADATA : Frames_1$4.Flags.NONE) | (isComplete ? Frames_1$4.Flags.COMPLETE : Frames_1$4.Flags.NONE),
          data: payload.data,
          metadata: payload.metadata
        });
      }
    };
    RequestChannelRequesterStream2.prototype.onComplete = function() {
      if (!this.streamId) {
        this.isComplete = true;
        return;
      }
      if (this.outboundDone) {
        return;
      }
      this.outboundDone = true;
      this.stream.send({
        type: Frames_1$4.FrameTypes.PAYLOAD,
        streamId: this.streamId,
        flags: Frames_1$4.Flags.COMPLETE,
        data: null,
        metadata: null
      });
      if (this.inboundDone) {
        this.stream.disconnect(this);
      }
    };
    RequestChannelRequesterStream2.prototype.onError = function(error) {
      if (this.outboundDone) {
        return;
      }
      var inboundDone = this.inboundDone;
      this.outboundDone = true;
      this.inboundDone = true;
      this.stream.send({
        type: Frames_1$4.FrameTypes.ERROR,
        streamId: this.streamId,
        flags: Frames_1$4.Flags.NONE,
        code: error instanceof Errors_1$4.RSocketError ? error.code : Errors_1$4.ErrorCodes.APPLICATION_ERROR,
        message: error.message
      });
      this.stream.disconnect(this);
      if (!inboundDone) {
        this.receiver.onError(error);
      }
    };
    RequestChannelRequesterStream2.prototype.onExtension = function(extendedType, content, canBeIgnored) {
      if (this.outboundDone) {
        return;
      }
      if (!this.streamId) {
        this.hasExtension = true;
        this.extendedType = extendedType;
        this.extendedContent = content;
        this.flags = canBeIgnored ? Frames_1$4.Flags.IGNORE : Frames_1$4.Flags.NONE;
        return;
      }
      this.stream.send({
        streamId: this.streamId,
        type: Frames_1$4.FrameTypes.EXT,
        extendedType,
        extendedContent: content,
        flags: canBeIgnored ? Frames_1$4.Flags.IGNORE : Frames_1$4.Flags.NONE
      });
    };
    RequestChannelRequesterStream2.prototype.close = function(error) {
      if (this.inboundDone && this.outboundDone) {
        return;
      }
      var inboundDone = this.inboundDone;
      var outboundDone = this.outboundDone;
      this.inboundDone = true;
      this.outboundDone = true;
      Reassembler$3.cancel(this);
      if (!outboundDone) {
        this.receiver.cancel();
      }
      if (!inboundDone) {
        if (error) {
          this.receiver.onError(error);
        } else {
          this.receiver.onComplete();
        }
      }
    };
    return RequestChannelRequesterStream2;
  }()
);
RequestChannelStream.RequestChannelRequesterStream = RequestChannelRequesterStream;
var RequestChannelResponderStream = (
  /** @class */
  function() {
    function RequestChannelResponderStream2(streamId, stream, fragmentSize, handler, frame) {
      this.streamId = streamId;
      this.stream = stream;
      this.fragmentSize = fragmentSize;
      this.handler = handler;
      this.streamType = Frames_1$4.FrameTypes.REQUEST_CHANNEL;
      stream.connect(this);
      if (Frames_1$4.Flags.hasFollows(frame.flags)) {
        Reassembler$3.add(this, frame.data, frame.metadata);
        this.initialRequestN = frame.requestN;
        this.isComplete = Frames_1$4.Flags.hasComplete(frame.flags);
        return;
      }
      var payload = {
        data: frame.data,
        metadata: frame.metadata
      };
      var hasComplete = Frames_1$4.Flags.hasComplete(frame.flags);
      this.inboundDone = hasComplete;
      try {
        this.receiver = handler(payload, frame.requestN, hasComplete, this);
        if (this.outboundDone && this.defferedError) {
          this.receiver.onError(this.defferedError);
        }
      } catch (error) {
        if (this.outboundDone && !this.inboundDone) {
          this.cancel();
        } else {
          this.inboundDone = true;
        }
        this.onError(error);
      }
    }
    RequestChannelResponderStream2.prototype.handle = function(frame) {
      var errorMessage;
      var frameType = frame.type;
      switch (frameType) {
        case Frames_1$4.FrameTypes.PAYLOAD: {
          if (Frames_1$4.Flags.hasFollows(frame.flags)) {
            if (Reassembler$3.add(this, frame.data, frame.metadata)) {
              return;
            }
            errorMessage = "Unexpected frame size";
            break;
          }
          var payload = this.hasFragments ? Reassembler$3.reassemble(this, frame.data, frame.metadata) : {
            data: frame.data,
            metadata: frame.metadata
          };
          var hasComplete = Frames_1$4.Flags.hasComplete(frame.flags);
          if (!this.receiver) {
            var inboundDone = this.isComplete || hasComplete;
            if (inboundDone) {
              this.inboundDone = true;
              if (this.outboundDone) {
                this.stream.disconnect(this);
              }
            }
            try {
              this.receiver = this.handler(payload, this.initialRequestN, inboundDone, this);
              if (this.outboundDone && this.defferedError) {
              }
            } catch (error2) {
              if (this.outboundDone && !this.inboundDone) {
                this.cancel();
              } else {
                this.inboundDone = true;
              }
              this.onError(error2);
            }
          } else {
            if (hasComplete) {
              this.inboundDone = true;
              if (this.outboundDone) {
                this.stream.disconnect(this);
              }
              if (!Frames_1$4.Flags.hasNext(frame.flags)) {
                this.receiver.onComplete();
                return;
              }
            }
            this.receiver.onNext(payload, hasComplete);
          }
          return;
        }
        case Frames_1$4.FrameTypes.REQUEST_N: {
          if (!this.receiver || this.hasFragments) {
            errorMessage = "Unexpected frame type [".concat(frameType, "] during reassembly");
            break;
          }
          this.receiver.request(frame.requestN);
          return;
        }
        case Frames_1$4.FrameTypes.ERROR:
        case Frames_1$4.FrameTypes.CANCEL: {
          var inboundDone = this.inboundDone;
          var outboundDone = this.outboundDone;
          this.inboundDone = true;
          this.outboundDone = true;
          this.stream.disconnect(this);
          Reassembler$3.cancel(this);
          if (!this.receiver) {
            return;
          }
          if (!outboundDone) {
            this.receiver.cancel();
          }
          if (!inboundDone) {
            var error = frameType === Frames_1$4.FrameTypes.CANCEL ? new Errors_1$4.RSocketError(Errors_1$4.ErrorCodes.CANCELED, "Cancelled") : new Errors_1$4.RSocketError(frame.code, frame.message);
            this.receiver.onError(error);
          }
          return;
        }
        case Frames_1$4.FrameTypes.EXT: {
          if (!this.receiver || this.hasFragments) {
            errorMessage = "Unexpected frame type [".concat(frameType, "] during reassembly");
            break;
          }
          this.receiver.onExtension(frame.extendedType, frame.extendedContent, Frames_1$4.Flags.hasIgnore(frame.flags));
          return;
        }
        default: {
          errorMessage = "Unexpected frame type [".concat(frameType, "]");
        }
      }
      this.stream.send({
        type: Frames_1$4.FrameTypes.ERROR,
        flags: Frames_1$4.Flags.NONE,
        code: Errors_1$4.ErrorCodes.CANCELED,
        message: errorMessage,
        streamId: this.streamId
      });
      this.stream.disconnect(this);
      this.close(new Errors_1$4.RSocketError(Errors_1$4.ErrorCodes.CANCELED, errorMessage));
    };
    RequestChannelResponderStream2.prototype.onError = function(error) {
      if (this.outboundDone) {
        console.warn("Trying to error for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      var inboundDone = this.inboundDone;
      this.outboundDone = true;
      this.inboundDone = true;
      this.stream.send({
        type: Frames_1$4.FrameTypes.ERROR,
        flags: Frames_1$4.Flags.NONE,
        code: error instanceof Errors_1$4.RSocketError ? error.code : Errors_1$4.ErrorCodes.APPLICATION_ERROR,
        message: error.message,
        streamId: this.streamId
      });
      this.stream.disconnect(this);
      if (!inboundDone) {
        if (this.receiver) {
          this.receiver.onError(error);
        } else {
          this.defferedError = error;
        }
      }
    };
    RequestChannelResponderStream2.prototype.onNext = function(payload, isCompletion) {
      var e_3, _a;
      if (this.outboundDone) {
        return;
      }
      if (isCompletion) {
        this.outboundDone = true;
      }
      if ((0, Fragmenter_1$3.isFragmentable)(payload, this.fragmentSize, Frames_1$4.FrameTypes.PAYLOAD)) {
        try {
          for (var _b = __values$3((0, Fragmenter_1$3.fragment)(this.streamId, payload, this.fragmentSize, Frames_1$4.FrameTypes.PAYLOAD, isCompletion)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            this.stream.send(frame);
          }
        } catch (e_3_1) {
          e_3 = { error: e_3_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_3) throw e_3.error;
          }
        }
      } else {
        this.stream.send({
          type: Frames_1$4.FrameTypes.PAYLOAD,
          flags: Frames_1$4.Flags.NEXT | (isCompletion ? Frames_1$4.Flags.COMPLETE : Frames_1$4.Flags.NONE) | (payload.metadata ? Frames_1$4.Flags.METADATA : Frames_1$4.Flags.NONE),
          data: payload.data,
          metadata: payload.metadata,
          streamId: this.streamId
        });
      }
      if (isCompletion && this.inboundDone) {
        this.stream.disconnect(this);
      }
    };
    RequestChannelResponderStream2.prototype.onComplete = function() {
      if (this.outboundDone) {
        return;
      }
      this.outboundDone = true;
      this.stream.send({
        type: Frames_1$4.FrameTypes.PAYLOAD,
        flags: Frames_1$4.Flags.COMPLETE,
        streamId: this.streamId,
        data: null,
        metadata: null
      });
      if (this.inboundDone) {
        this.stream.disconnect(this);
      }
    };
    RequestChannelResponderStream2.prototype.onExtension = function(extendedType, content, canBeIgnored) {
      if (this.outboundDone && this.inboundDone) {
        return;
      }
      this.stream.send({
        type: Frames_1$4.FrameTypes.EXT,
        streamId: this.streamId,
        flags: canBeIgnored ? Frames_1$4.Flags.IGNORE : Frames_1$4.Flags.NONE,
        extendedType,
        extendedContent: content
      });
    };
    RequestChannelResponderStream2.prototype.request = function(n) {
      if (this.inboundDone) {
        return;
      }
      this.stream.send({
        type: Frames_1$4.FrameTypes.REQUEST_N,
        flags: Frames_1$4.Flags.NONE,
        streamId: this.streamId,
        requestN: n
      });
    };
    RequestChannelResponderStream2.prototype.cancel = function() {
      if (this.inboundDone) {
        return;
      }
      this.inboundDone = true;
      this.stream.send({
        type: Frames_1$4.FrameTypes.CANCEL,
        flags: Frames_1$4.Flags.NONE,
        streamId: this.streamId
      });
      if (this.outboundDone) {
        this.stream.disconnect(this);
      }
    };
    RequestChannelResponderStream2.prototype.close = function(error) {
      if (this.inboundDone && this.outboundDone) {
        console.warn("Trying to close for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      var inboundDone = this.inboundDone;
      var outboundDone = this.outboundDone;
      this.inboundDone = true;
      this.outboundDone = true;
      Reassembler$3.cancel(this);
      var receiver = this.receiver;
      if (!receiver) {
        return;
      }
      if (!outboundDone) {
        receiver.cancel();
      }
      if (!inboundDone) {
        if (error) {
          receiver.onError(error);
        } else {
          receiver.onComplete();
        }
      }
    };
    return RequestChannelResponderStream2;
  }()
);
RequestChannelStream.RequestChannelResponderStream = RequestChannelResponderStream;
var RequestFnFStream = {};
var __createBinding$2 = commonjsGlobal && commonjsGlobal.__createBinding || (Object.create ? function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  Object.defineProperty(o, k2, { enumerable: true, get: function() {
    return m[k];
  } });
} : function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  o[k2] = m[k];
});
var __setModuleDefault$2 = commonjsGlobal && commonjsGlobal.__setModuleDefault || (Object.create ? function(o, v) {
  Object.defineProperty(o, "default", { enumerable: true, value: v });
} : function(o, v) {
  o["default"] = v;
});
var __importStar$2 = commonjsGlobal && commonjsGlobal.__importStar || function(mod) {
  if (mod && mod.__esModule) return mod;
  var result = {};
  if (mod != null) {
    for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding$2(result, mod, k);
  }
  __setModuleDefault$2(result, mod);
  return result;
};
var __values$2 = commonjsGlobal && commonjsGlobal.__values || function(o) {
  var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
  if (m) return m.call(o);
  if (o && typeof o.length === "number") return {
    next: function() {
      if (o && i >= o.length) o = void 0;
      return { value: o && o[i++], done: !o };
    }
  };
  throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
};
Object.defineProperty(RequestFnFStream, "__esModule", { value: true });
RequestFnFStream.RequestFnfResponderStream = RequestFnFStream.RequestFnFRequesterStream = void 0;
var Errors_1$3 = Errors;
var Fragmenter_1$2 = Fragmenter;
var Frames_1$3 = Frames;
var Reassembler$2 = __importStar$2(Reassembler$4);
var RequestFnFRequesterStream = (
  /** @class */
  function() {
    function RequestFnFRequesterStream2(payload, receiver, fragmentSize, leaseManager) {
      this.payload = payload;
      this.receiver = receiver;
      this.fragmentSize = fragmentSize;
      this.leaseManager = leaseManager;
      this.streamType = Frames_1$3.FrameTypes.REQUEST_FNF;
    }
    RequestFnFRequesterStream2.prototype.handleReady = function(streamId, stream) {
      var e_1, _a;
      if (this.done) {
        return false;
      }
      this.streamId = streamId;
      if ((0, Fragmenter_1$2.isFragmentable)(this.payload, this.fragmentSize, Frames_1$3.FrameTypes.REQUEST_FNF)) {
        try {
          for (var _b = __values$2((0, Fragmenter_1$2.fragment)(streamId, this.payload, this.fragmentSize, Frames_1$3.FrameTypes.REQUEST_FNF)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            stream.send(frame);
          }
        } catch (e_1_1) {
          e_1 = { error: e_1_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_1) throw e_1.error;
          }
        }
      } else {
        stream.send({
          type: Frames_1$3.FrameTypes.REQUEST_FNF,
          data: this.payload.data,
          metadata: this.payload.metadata,
          flags: this.payload.metadata ? Frames_1$3.Flags.METADATA : 0,
          streamId
        });
      }
      this.done = true;
      this.receiver.onComplete();
      return true;
    };
    RequestFnFRequesterStream2.prototype.handleReject = function(error) {
      if (this.done) {
        return;
      }
      this.done = true;
      this.receiver.onError(error);
    };
    RequestFnFRequesterStream2.prototype.cancel = function() {
      var _a;
      if (this.done) {
        return;
      }
      this.done = true;
      (_a = this.leaseManager) === null || _a === void 0 ? void 0 : _a.cancelRequest(this);
    };
    RequestFnFRequesterStream2.prototype.handle = function(frame) {
      if (frame.type == Frames_1$3.FrameTypes.ERROR) {
        this.close(new Errors_1$3.RSocketError(frame.code, frame.message));
        return;
      }
      this.close(new Errors_1$3.RSocketError(Errors_1$3.ErrorCodes.CANCELED, "Received invalid frame"));
    };
    RequestFnFRequesterStream2.prototype.close = function(error) {
      if (this.done) {
        console.warn("Trying to close for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      if (error) {
        this.receiver.onError(error);
      } else {
        this.receiver.onComplete();
      }
    };
    return RequestFnFRequesterStream2;
  }()
);
RequestFnFStream.RequestFnFRequesterStream = RequestFnFRequesterStream;
var RequestFnfResponderStream = (
  /** @class */
  function() {
    function RequestFnfResponderStream2(streamId, stream, handler, frame) {
      this.streamId = streamId;
      this.stream = stream;
      this.handler = handler;
      this.streamType = Frames_1$3.FrameTypes.REQUEST_FNF;
      if (Frames_1$3.Flags.hasFollows(frame.flags)) {
        Reassembler$2.add(this, frame.data, frame.metadata);
        stream.connect(this);
        return;
      }
      var payload = {
        data: frame.data,
        metadata: frame.metadata
      };
      try {
        this.cancellable = handler(payload, this);
      } catch (e) {
      }
    }
    RequestFnfResponderStream2.prototype.handle = function(frame) {
      var errorMessage;
      if (frame.type == Frames_1$3.FrameTypes.PAYLOAD) {
        if (Frames_1$3.Flags.hasFollows(frame.flags)) {
          if (Reassembler$2.add(this, frame.data, frame.metadata)) {
            return;
          }
          errorMessage = "Unexpected fragment size";
        } else {
          this.stream.disconnect(this);
          var payload = Reassembler$2.reassemble(this, frame.data, frame.metadata);
          try {
            this.cancellable = this.handler(payload, this);
          } catch (e) {
          }
          return;
        }
      } else {
        errorMessage = "Unexpected frame type [".concat(frame.type, "]");
      }
      this.done = true;
      if (frame.type != Frames_1$3.FrameTypes.CANCEL && frame.type != Frames_1$3.FrameTypes.ERROR) {
        this.stream.send({
          type: Frames_1$3.FrameTypes.ERROR,
          streamId: this.streamId,
          flags: Frames_1$3.Flags.NONE,
          code: Errors_1$3.ErrorCodes.CANCELED,
          message: errorMessage
        });
      }
      this.stream.disconnect(this);
      Reassembler$2.cancel(this);
    };
    RequestFnfResponderStream2.prototype.close = function(error) {
      var _a;
      if (this.done) {
        console.warn("Trying to close for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      this.done = true;
      Reassembler$2.cancel(this);
      (_a = this.cancellable) === null || _a === void 0 ? void 0 : _a.cancel();
    };
    RequestFnfResponderStream2.prototype.onError = function(error) {
    };
    RequestFnfResponderStream2.prototype.onComplete = function() {
    };
    return RequestFnfResponderStream2;
  }()
);
RequestFnFStream.RequestFnfResponderStream = RequestFnfResponderStream;
var RequestResponseStream = {};
var __createBinding$1 = commonjsGlobal && commonjsGlobal.__createBinding || (Object.create ? function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  Object.defineProperty(o, k2, { enumerable: true, get: function() {
    return m[k];
  } });
} : function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  o[k2] = m[k];
});
var __setModuleDefault$1 = commonjsGlobal && commonjsGlobal.__setModuleDefault || (Object.create ? function(o, v) {
  Object.defineProperty(o, "default", { enumerable: true, value: v });
} : function(o, v) {
  o["default"] = v;
});
var __importStar$1 = commonjsGlobal && commonjsGlobal.__importStar || function(mod) {
  if (mod && mod.__esModule) return mod;
  var result = {};
  if (mod != null) {
    for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding$1(result, mod, k);
  }
  __setModuleDefault$1(result, mod);
  return result;
};
var __values$1 = commonjsGlobal && commonjsGlobal.__values || function(o) {
  var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
  if (m) return m.call(o);
  if (o && typeof o.length === "number") return {
    next: function() {
      if (o && i >= o.length) o = void 0;
      return { value: o && o[i++], done: !o };
    }
  };
  throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
};
Object.defineProperty(RequestResponseStream, "__esModule", { value: true });
RequestResponseStream.RequestResponseResponderStream = RequestResponseStream.RequestResponseRequesterStream = void 0;
var Errors_1$2 = Errors;
var Fragmenter_1$1 = Fragmenter;
var Frames_1$2 = Frames;
var Reassembler$1 = __importStar$1(Reassembler$4);
var RequestResponseRequesterStream = (
  /** @class */
  function() {
    function RequestResponseRequesterStream2(payload, receiver, fragmentSize, leaseManager) {
      this.payload = payload;
      this.receiver = receiver;
      this.fragmentSize = fragmentSize;
      this.leaseManager = leaseManager;
      this.streamType = Frames_1$2.FrameTypes.REQUEST_RESPONSE;
    }
    RequestResponseRequesterStream2.prototype.handleReady = function(streamId, stream) {
      var e_1, _a;
      if (this.done) {
        return false;
      }
      this.streamId = streamId;
      this.stream = stream;
      stream.connect(this);
      if ((0, Fragmenter_1$1.isFragmentable)(this.payload, this.fragmentSize, Frames_1$2.FrameTypes.REQUEST_RESPONSE)) {
        try {
          for (var _b = __values$1((0, Fragmenter_1$1.fragment)(streamId, this.payload, this.fragmentSize, Frames_1$2.FrameTypes.REQUEST_RESPONSE)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            this.stream.send(frame);
          }
        } catch (e_1_1) {
          e_1 = { error: e_1_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_1) throw e_1.error;
          }
        }
      } else {
        this.stream.send({
          type: Frames_1$2.FrameTypes.REQUEST_RESPONSE,
          data: this.payload.data,
          metadata: this.payload.metadata,
          flags: this.payload.metadata ? Frames_1$2.Flags.METADATA : 0,
          streamId
        });
      }
      if (this.hasExtension) {
        this.stream.send({
          type: Frames_1$2.FrameTypes.EXT,
          streamId,
          extendedContent: this.extendedContent,
          extendedType: this.extendedType,
          flags: this.flags
        });
      }
      return true;
    };
    RequestResponseRequesterStream2.prototype.handleReject = function(error) {
      if (this.done) {
        return;
      }
      this.done = true;
      this.receiver.onError(error);
    };
    RequestResponseRequesterStream2.prototype.handle = function(frame) {
      var errorMessage;
      var frameType = frame.type;
      switch (frameType) {
        case Frames_1$2.FrameTypes.PAYLOAD: {
          var hasComplete = Frames_1$2.Flags.hasComplete(frame.flags);
          var hasPayload = Frames_1$2.Flags.hasNext(frame.flags);
          if (hasComplete || !Frames_1$2.Flags.hasFollows(frame.flags)) {
            this.done = true;
            this.stream.disconnect(this);
            if (!hasPayload) {
              this.receiver.onComplete();
              return;
            }
            var payload = this.hasFragments ? Reassembler$1.reassemble(this, frame.data, frame.metadata) : {
              data: frame.data,
              metadata: frame.metadata
            };
            this.receiver.onNext(payload, true);
            return;
          }
          if (!Reassembler$1.add(this, frame.data, frame.metadata)) {
            errorMessage = "Unexpected fragment size";
            break;
          }
          return;
        }
        case Frames_1$2.FrameTypes.ERROR: {
          this.done = true;
          this.stream.disconnect(this);
          Reassembler$1.cancel(this);
          this.receiver.onError(new Errors_1$2.RSocketError(frame.code, frame.message));
          return;
        }
        case Frames_1$2.FrameTypes.EXT: {
          if (this.hasFragments) {
            errorMessage = "Unexpected frame type [".concat(frameType, "] during reassembly");
            break;
          }
          this.receiver.onExtension(frame.extendedType, frame.extendedContent, Frames_1$2.Flags.hasIgnore(frame.flags));
          return;
        }
        default: {
          errorMessage = "Unexpected frame type [".concat(frameType, "]");
        }
      }
      this.close(new Errors_1$2.RSocketError(Errors_1$2.ErrorCodes.CANCELED, errorMessage));
      this.stream.send({
        type: Frames_1$2.FrameTypes.CANCEL,
        streamId: this.streamId,
        flags: Frames_1$2.Flags.NONE
      });
      this.stream.disconnect(this);
    };
    RequestResponseRequesterStream2.prototype.cancel = function() {
      var _a;
      if (this.done) {
        return;
      }
      this.done = true;
      if (!this.streamId) {
        (_a = this.leaseManager) === null || _a === void 0 ? void 0 : _a.cancelRequest(this);
        return;
      }
      this.stream.send({
        type: Frames_1$2.FrameTypes.CANCEL,
        flags: Frames_1$2.Flags.NONE,
        streamId: this.streamId
      });
      this.stream.disconnect(this);
      Reassembler$1.cancel(this);
    };
    RequestResponseRequesterStream2.prototype.onExtension = function(extendedType, content, canBeIgnored) {
      if (this.done) {
        return;
      }
      if (!this.streamId) {
        this.hasExtension = true;
        this.extendedType = extendedType;
        this.extendedContent = content;
        this.flags = canBeIgnored ? Frames_1$2.Flags.IGNORE : Frames_1$2.Flags.NONE;
        return;
      }
      this.stream.send({
        streamId: this.streamId,
        type: Frames_1$2.FrameTypes.EXT,
        extendedType,
        extendedContent: content,
        flags: canBeIgnored ? Frames_1$2.Flags.IGNORE : Frames_1$2.Flags.NONE
      });
    };
    RequestResponseRequesterStream2.prototype.close = function(error) {
      if (this.done) {
        return;
      }
      this.done = true;
      Reassembler$1.cancel(this);
      if (error) {
        this.receiver.onError(error);
      } else {
        this.receiver.onComplete();
      }
    };
    return RequestResponseRequesterStream2;
  }()
);
RequestResponseStream.RequestResponseRequesterStream = RequestResponseRequesterStream;
var RequestResponseResponderStream = (
  /** @class */
  function() {
    function RequestResponseResponderStream2(streamId, stream, fragmentSize, handler, frame) {
      this.streamId = streamId;
      this.stream = stream;
      this.fragmentSize = fragmentSize;
      this.handler = handler;
      this.streamType = Frames_1$2.FrameTypes.REQUEST_RESPONSE;
      stream.connect(this);
      if (Frames_1$2.Flags.hasFollows(frame.flags)) {
        Reassembler$1.add(this, frame.data, frame.metadata);
        return;
      }
      var payload = {
        data: frame.data,
        metadata: frame.metadata
      };
      try {
        this.receiver = handler(payload, this);
      } catch (error) {
        this.onError(error);
      }
    }
    RequestResponseResponderStream2.prototype.handle = function(frame) {
      var _a;
      var errorMessage;
      if (!this.receiver || this.hasFragments) {
        if (frame.type === Frames_1$2.FrameTypes.PAYLOAD) {
          if (Frames_1$2.Flags.hasFollows(frame.flags)) {
            if (Reassembler$1.add(this, frame.data, frame.metadata)) {
              return;
            }
            errorMessage = "Unexpected fragment size";
          } else {
            var payload = Reassembler$1.reassemble(this, frame.data, frame.metadata);
            try {
              this.receiver = this.handler(payload, this);
            } catch (error) {
              this.onError(error);
            }
            return;
          }
        } else {
          errorMessage = "Unexpected frame type [".concat(frame.type, "] during reassembly");
        }
      } else if (frame.type === Frames_1$2.FrameTypes.EXT) {
        this.receiver.onExtension(frame.extendedType, frame.extendedContent, Frames_1$2.Flags.hasIgnore(frame.flags));
        return;
      } else {
        errorMessage = "Unexpected frame type [".concat(frame.type, "]");
      }
      this.done = true;
      (_a = this.receiver) === null || _a === void 0 ? void 0 : _a.cancel();
      if (frame.type !== Frames_1$2.FrameTypes.CANCEL && frame.type !== Frames_1$2.FrameTypes.ERROR) {
        this.stream.send({
          type: Frames_1$2.FrameTypes.ERROR,
          flags: Frames_1$2.Flags.NONE,
          code: Errors_1$2.ErrorCodes.CANCELED,
          message: errorMessage,
          streamId: this.streamId
        });
      }
      this.stream.disconnect(this);
      Reassembler$1.cancel(this);
    };
    RequestResponseResponderStream2.prototype.onError = function(error) {
      if (this.done) {
        console.warn("Trying to error for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      this.done = true;
      this.stream.send({
        type: Frames_1$2.FrameTypes.ERROR,
        flags: Frames_1$2.Flags.NONE,
        code: error instanceof Errors_1$2.RSocketError ? error.code : Errors_1$2.ErrorCodes.APPLICATION_ERROR,
        message: error.message,
        streamId: this.streamId
      });
      this.stream.disconnect(this);
    };
    RequestResponseResponderStream2.prototype.onNext = function(payload, isCompletion) {
      var e_2, _a;
      if (this.done) {
        return;
      }
      this.done = true;
      if ((0, Fragmenter_1$1.isFragmentable)(payload, this.fragmentSize, Frames_1$2.FrameTypes.PAYLOAD)) {
        try {
          for (var _b = __values$1((0, Fragmenter_1$1.fragment)(this.streamId, payload, this.fragmentSize, Frames_1$2.FrameTypes.PAYLOAD, true)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            this.stream.send(frame);
          }
        } catch (e_2_1) {
          e_2 = { error: e_2_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_2) throw e_2.error;
          }
        }
      } else {
        this.stream.send({
          type: Frames_1$2.FrameTypes.PAYLOAD,
          flags: Frames_1$2.Flags.NEXT | Frames_1$2.Flags.COMPLETE | (payload.metadata ? Frames_1$2.Flags.METADATA : 0),
          data: payload.data,
          metadata: payload.metadata,
          streamId: this.streamId
        });
      }
      this.stream.disconnect(this);
    };
    RequestResponseResponderStream2.prototype.onComplete = function() {
      if (this.done) {
        return;
      }
      this.done = true;
      this.stream.send({
        type: Frames_1$2.FrameTypes.PAYLOAD,
        flags: Frames_1$2.Flags.COMPLETE,
        streamId: this.streamId,
        data: null,
        metadata: null
      });
      this.stream.disconnect(this);
    };
    RequestResponseResponderStream2.prototype.onExtension = function(extendedType, content, canBeIgnored) {
      if (this.done) {
        return;
      }
      this.stream.send({
        type: Frames_1$2.FrameTypes.EXT,
        streamId: this.streamId,
        flags: canBeIgnored ? Frames_1$2.Flags.IGNORE : Frames_1$2.Flags.NONE,
        extendedType,
        extendedContent: content
      });
    };
    RequestResponseResponderStream2.prototype.close = function(error) {
      var _a;
      if (this.done) {
        console.warn("Trying to close for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      Reassembler$1.cancel(this);
      (_a = this.receiver) === null || _a === void 0 ? void 0 : _a.cancel();
    };
    return RequestResponseResponderStream2;
  }()
);
RequestResponseStream.RequestResponseResponderStream = RequestResponseResponderStream;
var RequestStreamStream = {};
var __createBinding = commonjsGlobal && commonjsGlobal.__createBinding || (Object.create ? function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  Object.defineProperty(o, k2, { enumerable: true, get: function() {
    return m[k];
  } });
} : function(o, m, k, k2) {
  if (k2 === void 0) k2 = k;
  o[k2] = m[k];
});
var __setModuleDefault = commonjsGlobal && commonjsGlobal.__setModuleDefault || (Object.create ? function(o, v) {
  Object.defineProperty(o, "default", { enumerable: true, value: v });
} : function(o, v) {
  o["default"] = v;
});
var __importStar = commonjsGlobal && commonjsGlobal.__importStar || function(mod) {
  if (mod && mod.__esModule) return mod;
  var result = {};
  if (mod != null) {
    for (var k in mod) if (k !== "default" && Object.prototype.hasOwnProperty.call(mod, k)) __createBinding(result, mod, k);
  }
  __setModuleDefault(result, mod);
  return result;
};
var __values = commonjsGlobal && commonjsGlobal.__values || function(o) {
  var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
  if (m) return m.call(o);
  if (o && typeof o.length === "number") return {
    next: function() {
      if (o && i >= o.length) o = void 0;
      return { value: o && o[i++], done: !o };
    }
  };
  throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
};
Object.defineProperty(RequestStreamStream, "__esModule", { value: true });
RequestStreamStream.RequestStreamResponderStream = RequestStreamStream.RequestStreamRequesterStream = void 0;
var Errors_1$1 = Errors;
var Fragmenter_1 = Fragmenter;
var Frames_1$1 = Frames;
var Reassembler = __importStar(Reassembler$4);
var RequestStreamRequesterStream = (
  /** @class */
  function() {
    function RequestStreamRequesterStream2(payload, receiver, fragmentSize, initialRequestN, leaseManager) {
      this.payload = payload;
      this.receiver = receiver;
      this.fragmentSize = fragmentSize;
      this.initialRequestN = initialRequestN;
      this.leaseManager = leaseManager;
      this.streamType = Frames_1$1.FrameTypes.REQUEST_STREAM;
    }
    RequestStreamRequesterStream2.prototype.handleReady = function(streamId, stream) {
      var e_1, _a;
      if (this.done) {
        return false;
      }
      this.streamId = streamId;
      this.stream = stream;
      stream.connect(this);
      if ((0, Fragmenter_1.isFragmentable)(this.payload, this.fragmentSize, Frames_1$1.FrameTypes.REQUEST_STREAM)) {
        try {
          for (var _b = __values((0, Fragmenter_1.fragmentWithRequestN)(streamId, this.payload, this.fragmentSize, Frames_1$1.FrameTypes.REQUEST_STREAM, this.initialRequestN)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            this.stream.send(frame);
          }
        } catch (e_1_1) {
          e_1 = { error: e_1_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_1) throw e_1.error;
          }
        }
      } else {
        this.stream.send({
          type: Frames_1$1.FrameTypes.REQUEST_STREAM,
          data: this.payload.data,
          metadata: this.payload.metadata,
          requestN: this.initialRequestN,
          flags: this.payload.metadata !== void 0 ? Frames_1$1.Flags.METADATA : 0,
          streamId
        });
      }
      if (this.hasExtension) {
        this.stream.send({
          type: Frames_1$1.FrameTypes.EXT,
          streamId,
          extendedContent: this.extendedContent,
          extendedType: this.extendedType,
          flags: this.flags
        });
      }
      return true;
    };
    RequestStreamRequesterStream2.prototype.handleReject = function(error) {
      if (this.done) {
        return;
      }
      this.done = true;
      this.receiver.onError(error);
    };
    RequestStreamRequesterStream2.prototype.handle = function(frame) {
      var errorMessage;
      var frameType = frame.type;
      switch (frameType) {
        case Frames_1$1.FrameTypes.PAYLOAD: {
          var hasComplete = Frames_1$1.Flags.hasComplete(frame.flags);
          var hasNext = Frames_1$1.Flags.hasNext(frame.flags);
          if (hasComplete || !Frames_1$1.Flags.hasFollows(frame.flags)) {
            if (hasComplete) {
              this.done = true;
              this.stream.disconnect(this);
              if (!hasNext) {
                this.receiver.onComplete();
                return;
              }
            }
            var payload = this.hasFragments ? Reassembler.reassemble(this, frame.data, frame.metadata) : {
              data: frame.data,
              metadata: frame.metadata
            };
            this.receiver.onNext(payload, hasComplete);
            return;
          }
          if (!Reassembler.add(this, frame.data, frame.metadata)) {
            errorMessage = "Unexpected fragment size";
            break;
          }
          return;
        }
        case Frames_1$1.FrameTypes.ERROR: {
          this.done = true;
          this.stream.disconnect(this);
          Reassembler.cancel(this);
          this.receiver.onError(new Errors_1$1.RSocketError(frame.code, frame.message));
          return;
        }
        case Frames_1$1.FrameTypes.EXT: {
          if (this.hasFragments) {
            errorMessage = "Unexpected frame type [".concat(frameType, "] during reassembly");
            break;
          }
          this.receiver.onExtension(frame.extendedType, frame.extendedContent, Frames_1$1.Flags.hasIgnore(frame.flags));
          return;
        }
        default: {
          errorMessage = "Unexpected frame type [".concat(frameType, "]");
        }
      }
      this.close(new Errors_1$1.RSocketError(Errors_1$1.ErrorCodes.CANCELED, errorMessage));
      this.stream.send({
        type: Frames_1$1.FrameTypes.CANCEL,
        streamId: this.streamId,
        flags: Frames_1$1.Flags.NONE
      });
      this.stream.disconnect(this);
    };
    RequestStreamRequesterStream2.prototype.request = function(n) {
      if (this.done) {
        return;
      }
      if (!this.streamId) {
        this.initialRequestN += n;
        return;
      }
      this.stream.send({
        type: Frames_1$1.FrameTypes.REQUEST_N,
        flags: Frames_1$1.Flags.NONE,
        requestN: n,
        streamId: this.streamId
      });
    };
    RequestStreamRequesterStream2.prototype.cancel = function() {
      var _a;
      if (this.done) {
        return;
      }
      this.done = true;
      if (!this.streamId) {
        (_a = this.leaseManager) === null || _a === void 0 ? void 0 : _a.cancelRequest(this);
        return;
      }
      this.stream.send({
        type: Frames_1$1.FrameTypes.CANCEL,
        flags: Frames_1$1.Flags.NONE,
        streamId: this.streamId
      });
      this.stream.disconnect(this);
      Reassembler.cancel(this);
    };
    RequestStreamRequesterStream2.prototype.onExtension = function(extendedType, content, canBeIgnored) {
      if (this.done) {
        return;
      }
      if (!this.streamId) {
        this.hasExtension = true;
        this.extendedType = extendedType;
        this.extendedContent = content;
        this.flags = canBeIgnored ? Frames_1$1.Flags.IGNORE : Frames_1$1.Flags.NONE;
        return;
      }
      this.stream.send({
        streamId: this.streamId,
        type: Frames_1$1.FrameTypes.EXT,
        extendedType,
        extendedContent: content,
        flags: canBeIgnored ? Frames_1$1.Flags.IGNORE : Frames_1$1.Flags.NONE
      });
    };
    RequestStreamRequesterStream2.prototype.close = function(error) {
      if (this.done) {
        return;
      }
      this.done = true;
      Reassembler.cancel(this);
      if (error) {
        this.receiver.onError(error);
      } else {
        this.receiver.onComplete();
      }
    };
    return RequestStreamRequesterStream2;
  }()
);
RequestStreamStream.RequestStreamRequesterStream = RequestStreamRequesterStream;
var RequestStreamResponderStream = (
  /** @class */
  function() {
    function RequestStreamResponderStream2(streamId, stream, fragmentSize, handler, frame) {
      this.streamId = streamId;
      this.stream = stream;
      this.fragmentSize = fragmentSize;
      this.handler = handler;
      this.streamType = Frames_1$1.FrameTypes.REQUEST_STREAM;
      stream.connect(this);
      if (Frames_1$1.Flags.hasFollows(frame.flags)) {
        this.initialRequestN = frame.requestN;
        Reassembler.add(this, frame.data, frame.metadata);
        return;
      }
      var payload = {
        data: frame.data,
        metadata: frame.metadata
      };
      try {
        this.receiver = handler(payload, frame.requestN, this);
      } catch (error) {
        this.onError(error);
      }
    }
    RequestStreamResponderStream2.prototype.handle = function(frame) {
      var _a;
      var errorMessage;
      if (!this.receiver || this.hasFragments) {
        if (frame.type === Frames_1$1.FrameTypes.PAYLOAD) {
          if (Frames_1$1.Flags.hasFollows(frame.flags)) {
            if (Reassembler.add(this, frame.data, frame.metadata)) {
              return;
            }
            errorMessage = "Unexpected frame size";
          } else {
            var payload = Reassembler.reassemble(this, frame.data, frame.metadata);
            try {
              this.receiver = this.handler(payload, this.initialRequestN, this);
            } catch (error) {
              this.onError(error);
            }
            return;
          }
        } else {
          errorMessage = "Unexpected frame type [".concat(frame.type, "] during reassembly");
        }
      } else if (frame.type === Frames_1$1.FrameTypes.REQUEST_N) {
        this.receiver.request(frame.requestN);
        return;
      } else if (frame.type === Frames_1$1.FrameTypes.EXT) {
        this.receiver.onExtension(frame.extendedType, frame.extendedContent, Frames_1$1.Flags.hasIgnore(frame.flags));
        return;
      } else {
        errorMessage = "Unexpected frame type [".concat(frame.type, "]");
      }
      this.done = true;
      Reassembler.cancel(this);
      (_a = this.receiver) === null || _a === void 0 ? void 0 : _a.cancel();
      if (frame.type !== Frames_1$1.FrameTypes.CANCEL && frame.type !== Frames_1$1.FrameTypes.ERROR) {
        this.stream.send({
          type: Frames_1$1.FrameTypes.ERROR,
          flags: Frames_1$1.Flags.NONE,
          code: Errors_1$1.ErrorCodes.CANCELED,
          message: errorMessage,
          streamId: this.streamId
        });
      }
      this.stream.disconnect(this);
    };
    RequestStreamResponderStream2.prototype.onError = function(error) {
      if (this.done) {
        console.warn("Trying to error for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      this.done = true;
      this.stream.send({
        type: Frames_1$1.FrameTypes.ERROR,
        flags: Frames_1$1.Flags.NONE,
        code: error instanceof Errors_1$1.RSocketError ? error.code : Errors_1$1.ErrorCodes.APPLICATION_ERROR,
        message: error.message,
        streamId: this.streamId
      });
      this.stream.disconnect(this);
    };
    RequestStreamResponderStream2.prototype.onNext = function(payload, isCompletion) {
      var e_2, _a;
      if (this.done) {
        return;
      }
      if (isCompletion) {
        this.done = true;
      }
      if ((0, Fragmenter_1.isFragmentable)(payload, this.fragmentSize, Frames_1$1.FrameTypes.PAYLOAD)) {
        try {
          for (var _b = __values((0, Fragmenter_1.fragment)(this.streamId, payload, this.fragmentSize, Frames_1$1.FrameTypes.PAYLOAD, isCompletion)), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            this.stream.send(frame);
          }
        } catch (e_2_1) {
          e_2 = { error: e_2_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_2) throw e_2.error;
          }
        }
      } else {
        this.stream.send({
          type: Frames_1$1.FrameTypes.PAYLOAD,
          flags: Frames_1$1.Flags.NEXT | (isCompletion ? Frames_1$1.Flags.COMPLETE : Frames_1$1.Flags.NONE) | (payload.metadata ? Frames_1$1.Flags.METADATA : Frames_1$1.Flags.NONE),
          data: payload.data,
          metadata: payload.metadata,
          streamId: this.streamId
        });
      }
      if (isCompletion) {
        this.stream.disconnect(this);
      }
    };
    RequestStreamResponderStream2.prototype.onComplete = function() {
      if (this.done) {
        return;
      }
      this.done = true;
      this.stream.send({
        type: Frames_1$1.FrameTypes.PAYLOAD,
        flags: Frames_1$1.Flags.COMPLETE,
        streamId: this.streamId,
        data: null,
        metadata: null
      });
      this.stream.disconnect(this);
    };
    RequestStreamResponderStream2.prototype.onExtension = function(extendedType, content, canBeIgnored) {
      if (this.done) {
        return;
      }
      this.stream.send({
        type: Frames_1$1.FrameTypes.EXT,
        streamId: this.streamId,
        flags: canBeIgnored ? Frames_1$1.Flags.IGNORE : Frames_1$1.Flags.NONE,
        extendedType,
        extendedContent: content
      });
    };
    RequestStreamResponderStream2.prototype.close = function(error) {
      var _a;
      if (this.done) {
        console.warn("Trying to close for the second time. ".concat(error ? "Dropping error [".concat(error, "].") : ""));
        return;
      }
      Reassembler.cancel(this);
      (_a = this.receiver) === null || _a === void 0 ? void 0 : _a.cancel();
    };
    return RequestStreamResponderStream2;
  }()
);
RequestStreamStream.RequestStreamResponderStream = RequestStreamResponderStream;
Object.defineProperty(RSocketSupport, "__esModule", { value: true });
RSocketSupport.KeepAliveSender = RSocketSupport.KeepAliveHandler = RSocketSupport.DefaultConnectionFrameHandler = RSocketSupport.DefaultStreamRequestHandler = RSocketSupport.LeaseHandler = RSocketSupport.RSocketRequester = void 0;
var Errors_1 = Errors;
var Frames_1 = Frames;
var RequestChannelStream_1 = RequestChannelStream;
var RequestFnFStream_1 = RequestFnFStream;
var RequestResponseStream_1 = RequestResponseStream;
var RequestStreamStream_1 = RequestStreamStream;
var RSocketRequester = (
  /** @class */
  function() {
    function RSocketRequester2(connection, fragmentSize, leaseManager) {
      this.connection = connection;
      this.fragmentSize = fragmentSize;
      this.leaseManager = leaseManager;
    }
    RSocketRequester2.prototype.fireAndForget = function(payload, responderStream) {
      var handler = new RequestFnFStream_1.RequestFnFRequesterStream(payload, responderStream, this.fragmentSize, this.leaseManager);
      if (this.leaseManager) {
        this.leaseManager.requestLease(handler);
      } else {
        this.connection.multiplexerDemultiplexer.createRequestStream(handler);
      }
      return handler;
    };
    RSocketRequester2.prototype.requestResponse = function(payload, responderStream) {
      var handler = new RequestResponseStream_1.RequestResponseRequesterStream(payload, responderStream, this.fragmentSize, this.leaseManager);
      if (this.leaseManager) {
        this.leaseManager.requestLease(handler);
      } else {
        this.connection.multiplexerDemultiplexer.createRequestStream(handler);
      }
      return handler;
    };
    RSocketRequester2.prototype.requestStream = function(payload, initialRequestN, responderStream) {
      var handler = new RequestStreamStream_1.RequestStreamRequesterStream(payload, responderStream, this.fragmentSize, initialRequestN, this.leaseManager);
      if (this.leaseManager) {
        this.leaseManager.requestLease(handler);
      } else {
        this.connection.multiplexerDemultiplexer.createRequestStream(handler);
      }
      return handler;
    };
    RSocketRequester2.prototype.requestChannel = function(payload, initialRequestN, isCompleted, responderStream) {
      var handler = new RequestChannelStream_1.RequestChannelRequesterStream(payload, isCompleted, responderStream, this.fragmentSize, initialRequestN, this.leaseManager);
      if (this.leaseManager) {
        this.leaseManager.requestLease(handler);
      } else {
        this.connection.multiplexerDemultiplexer.createRequestStream(handler);
      }
      return handler;
    };
    RSocketRequester2.prototype.metadataPush = function(metadata, responderStream) {
      throw new Error("Method not implemented.");
    };
    RSocketRequester2.prototype.close = function(error) {
      this.connection.close(error);
    };
    RSocketRequester2.prototype.onClose = function(callback) {
      this.connection.onClose(callback);
    };
    return RSocketRequester2;
  }()
);
RSocketSupport.RSocketRequester = RSocketRequester;
var LeaseHandler = (
  /** @class */
  function() {
    function LeaseHandler2(maxPendingRequests, multiplexer) {
      this.maxPendingRequests = maxPendingRequests;
      this.multiplexer = multiplexer;
      this.pendingRequests = [];
      this.expirationTime = 0;
      this.availableLease = 0;
    }
    LeaseHandler2.prototype.handle = function(frame) {
      this.expirationTime = frame.ttl + Date.now();
      this.availableLease = frame.requestCount;
      while (this.availableLease > 0 && this.pendingRequests.length > 0) {
        var handler = this.pendingRequests.shift();
        this.availableLease--;
        this.multiplexer.createRequestStream(handler);
      }
    };
    LeaseHandler2.prototype.requestLease = function(handler) {
      var availableLease = this.availableLease;
      if (availableLease > 0 && Date.now() < this.expirationTime) {
        this.availableLease = availableLease - 1;
        this.multiplexer.createRequestStream(handler);
        return;
      }
      if (this.pendingRequests.length >= this.maxPendingRequests) {
        handler.handleReject(new Errors_1.RSocketError(Errors_1.ErrorCodes.REJECTED, "No available lease given"));
        return;
      }
      this.pendingRequests.push(handler);
    };
    LeaseHandler2.prototype.cancelRequest = function(handler) {
      var index = this.pendingRequests.indexOf(handler);
      if (index > -1) {
        this.pendingRequests.splice(index, 1);
      }
    };
    return LeaseHandler2;
  }()
);
RSocketSupport.LeaseHandler = LeaseHandler;
var DefaultStreamRequestHandler = (
  /** @class */
  function() {
    function DefaultStreamRequestHandler2(rsocket, fragmentSize) {
      this.rsocket = rsocket;
      this.fragmentSize = fragmentSize;
    }
    DefaultStreamRequestHandler2.prototype.handle = function(frame, stream) {
      switch (frame.type) {
        case Frames_1.FrameTypes.REQUEST_FNF:
          if (this.rsocket.fireAndForget) {
            new RequestFnFStream_1.RequestFnfResponderStream(frame.streamId, stream, this.rsocket.fireAndForget.bind(this.rsocket), frame);
          }
          return;
        case Frames_1.FrameTypes.REQUEST_RESPONSE:
          if (this.rsocket.requestResponse) {
            new RequestResponseStream_1.RequestResponseResponderStream(frame.streamId, stream, this.fragmentSize, this.rsocket.requestResponse.bind(this.rsocket), frame);
            return;
          }
          this.rejectRequest(frame.streamId, stream);
          return;
        case Frames_1.FrameTypes.REQUEST_STREAM:
          if (this.rsocket.requestStream) {
            new RequestStreamStream_1.RequestStreamResponderStream(frame.streamId, stream, this.fragmentSize, this.rsocket.requestStream.bind(this.rsocket), frame);
            return;
          }
          this.rejectRequest(frame.streamId, stream);
          return;
        case Frames_1.FrameTypes.REQUEST_CHANNEL:
          if (this.rsocket.requestChannel) {
            new RequestChannelStream_1.RequestChannelResponderStream(frame.streamId, stream, this.fragmentSize, this.rsocket.requestChannel.bind(this.rsocket), frame);
            return;
          }
          this.rejectRequest(frame.streamId, stream);
          return;
      }
    };
    DefaultStreamRequestHandler2.prototype.rejectRequest = function(streamId, stream) {
      stream.send({
        type: Frames_1.FrameTypes.ERROR,
        streamId,
        flags: Frames_1.Flags.NONE,
        code: Errors_1.ErrorCodes.REJECTED,
        message: "No available handler found"
      });
    };
    DefaultStreamRequestHandler2.prototype.close = function() {
    };
    return DefaultStreamRequestHandler2;
  }()
);
RSocketSupport.DefaultStreamRequestHandler = DefaultStreamRequestHandler;
var DefaultConnectionFrameHandler = (
  /** @class */
  function() {
    function DefaultConnectionFrameHandler2(connection, keepAliveHandler, keepAliveSender, leaseHandler, rsocket) {
      this.connection = connection;
      this.keepAliveHandler = keepAliveHandler;
      this.keepAliveSender = keepAliveSender;
      this.leaseHandler = leaseHandler;
      this.rsocket = rsocket;
    }
    DefaultConnectionFrameHandler2.prototype.handle = function(frame) {
      switch (frame.type) {
        case Frames_1.FrameTypes.KEEPALIVE:
          this.keepAliveHandler.handle(frame);
          return;
        case Frames_1.FrameTypes.LEASE:
          if (this.leaseHandler) {
            this.leaseHandler.handle(frame);
            return;
          }
          return;
        case Frames_1.FrameTypes.ERROR:
          this.connection.close(new Errors_1.RSocketError(frame.code, frame.message));
          return;
        case Frames_1.FrameTypes.METADATA_PUSH:
          if (this.rsocket.metadataPush) ;
          return;
        default:
          this.connection.multiplexerDemultiplexer.connectionOutbound.send({
            type: Frames_1.FrameTypes.ERROR,
            streamId: 0,
            flags: Frames_1.Flags.NONE,
            message: "Received unknown frame type",
            code: Errors_1.ErrorCodes.CONNECTION_ERROR
          });
      }
    };
    DefaultConnectionFrameHandler2.prototype.pause = function() {
      var _a;
      this.keepAliveHandler.pause();
      (_a = this.keepAliveSender) === null || _a === void 0 ? void 0 : _a.pause();
    };
    DefaultConnectionFrameHandler2.prototype.resume = function() {
      var _a;
      this.keepAliveHandler.start();
      (_a = this.keepAliveSender) === null || _a === void 0 ? void 0 : _a.start();
    };
    DefaultConnectionFrameHandler2.prototype.close = function(error) {
      var _a;
      this.keepAliveHandler.close();
      (_a = this.rsocket.close) === null || _a === void 0 ? void 0 : _a.call(this.rsocket, error);
    };
    return DefaultConnectionFrameHandler2;
  }()
);
RSocketSupport.DefaultConnectionFrameHandler = DefaultConnectionFrameHandler;
var KeepAliveHandlerStates;
(function(KeepAliveHandlerStates2) {
  KeepAliveHandlerStates2[KeepAliveHandlerStates2["Paused"] = 0] = "Paused";
  KeepAliveHandlerStates2[KeepAliveHandlerStates2["Running"] = 1] = "Running";
  KeepAliveHandlerStates2[KeepAliveHandlerStates2["Closed"] = 2] = "Closed";
})(KeepAliveHandlerStates || (KeepAliveHandlerStates = {}));
var KeepAliveHandler = (
  /** @class */
  function() {
    function KeepAliveHandler2(connection, keepAliveTimeoutDuration) {
      this.connection = connection;
      this.keepAliveTimeoutDuration = keepAliveTimeoutDuration;
      this.state = KeepAliveHandlerStates.Paused;
      this.outbound = connection.multiplexerDemultiplexer.connectionOutbound;
    }
    KeepAliveHandler2.prototype.handle = function(frame) {
      this.keepAliveLastReceivedMillis = Date.now();
      if (Frames_1.Flags.hasRespond(frame.flags)) {
        this.outbound.send({
          type: Frames_1.FrameTypes.KEEPALIVE,
          streamId: 0,
          data: frame.data,
          flags: frame.flags ^ Frames_1.Flags.RESPOND,
          lastReceivedPosition: 0
        });
      }
    };
    KeepAliveHandler2.prototype.start = function() {
      if (this.state !== KeepAliveHandlerStates.Paused) {
        return;
      }
      this.keepAliveLastReceivedMillis = Date.now();
      this.state = KeepAliveHandlerStates.Running;
      this.activeTimeout = setTimeout(this.timeoutCheck.bind(this), this.keepAliveTimeoutDuration);
    };
    KeepAliveHandler2.prototype.pause = function() {
      if (this.state !== KeepAliveHandlerStates.Running) {
        return;
      }
      this.state = KeepAliveHandlerStates.Paused;
      clearTimeout(this.activeTimeout);
    };
    KeepAliveHandler2.prototype.close = function() {
      this.state = KeepAliveHandlerStates.Closed;
      clearTimeout(this.activeTimeout);
    };
    KeepAliveHandler2.prototype.timeoutCheck = function() {
      var now = Date.now();
      var noKeepAliveDuration = now - this.keepAliveLastReceivedMillis;
      if (noKeepAliveDuration >= this.keepAliveTimeoutDuration) {
        this.connection.close(new Error("No keep-alive acks for ".concat(this.keepAliveTimeoutDuration, " millis")));
      } else {
        this.activeTimeout = setTimeout(this.timeoutCheck.bind(this), Math.max(100, this.keepAliveTimeoutDuration - noKeepAliveDuration));
      }
    };
    return KeepAliveHandler2;
  }()
);
RSocketSupport.KeepAliveHandler = KeepAliveHandler;
var KeepAliveSenderStates;
(function(KeepAliveSenderStates2) {
  KeepAliveSenderStates2[KeepAliveSenderStates2["Paused"] = 0] = "Paused";
  KeepAliveSenderStates2[KeepAliveSenderStates2["Running"] = 1] = "Running";
  KeepAliveSenderStates2[KeepAliveSenderStates2["Closed"] = 2] = "Closed";
})(KeepAliveSenderStates || (KeepAliveSenderStates = {}));
var KeepAliveSender = (
  /** @class */
  function() {
    function KeepAliveSender2(outbound, keepAlivePeriodDuration) {
      this.outbound = outbound;
      this.keepAlivePeriodDuration = keepAlivePeriodDuration;
      this.state = KeepAliveSenderStates.Paused;
    }
    KeepAliveSender2.prototype.sendKeepAlive = function() {
      this.outbound.send({
        type: Frames_1.FrameTypes.KEEPALIVE,
        streamId: 0,
        data: void 0,
        flags: Frames_1.Flags.RESPOND,
        lastReceivedPosition: 0
      });
    };
    KeepAliveSender2.prototype.start = function() {
      if (this.state !== KeepAliveSenderStates.Paused) {
        return;
      }
      this.state = KeepAliveSenderStates.Running;
      this.activeInterval = setInterval(this.sendKeepAlive.bind(this), this.keepAlivePeriodDuration);
    };
    KeepAliveSender2.prototype.pause = function() {
      if (this.state !== KeepAliveSenderStates.Running) {
        return;
      }
      this.state = KeepAliveSenderStates.Paused;
      clearInterval(this.activeInterval);
    };
    KeepAliveSender2.prototype.close = function() {
      this.state = KeepAliveSenderStates.Closed;
      clearInterval(this.activeInterval);
    };
    return KeepAliveSender2;
  }()
);
RSocketSupport.KeepAliveSender = KeepAliveSender;
var Resume = {};
var hasRequiredResume;
function requireResume() {
  if (hasRequiredResume) return Resume;
  hasRequiredResume = 1;
  var __values2 = commonjsGlobal && commonjsGlobal.__values || function(o) {
    var s = typeof Symbol === "function" && Symbol.iterator, m = s && o[s], i = 0;
    if (m) return m.call(o);
    if (o && typeof o.length === "number") return {
      next: function() {
        if (o && i >= o.length) o = void 0;
        return { value: o && o[i++], done: !o };
      }
    };
    throw new TypeError(s ? "Object is not iterable." : "Symbol.iterator is not defined.");
  };
  Object.defineProperty(Resume, "__esModule", { value: true });
  Resume.FrameStore = void 0;
  var _1 = requireDist();
  var Codecs_1 = Codecs;
  var FrameStore = (
    /** @class */
    function() {
      function FrameStore2() {
        this.storedFrames = [];
        this._lastReceivedFramePosition = 0;
        this._firstAvailableFramePosition = 0;
        this._lastSentFramePosition = 0;
      }
      Object.defineProperty(FrameStore2.prototype, "lastReceivedFramePosition", {
        get: function() {
          return this._lastReceivedFramePosition;
        },
        enumerable: false,
        configurable: true
      });
      Object.defineProperty(FrameStore2.prototype, "firstAvailableFramePosition", {
        get: function() {
          return this._firstAvailableFramePosition;
        },
        enumerable: false,
        configurable: true
      });
      Object.defineProperty(FrameStore2.prototype, "lastSentFramePosition", {
        get: function() {
          return this._lastSentFramePosition;
        },
        enumerable: false,
        configurable: true
      });
      FrameStore2.prototype.store = function(frame) {
        this._lastSentFramePosition += (0, Codecs_1.sizeOfFrame)(frame);
        this.storedFrames.push(frame);
      };
      FrameStore2.prototype.record = function(frame) {
        this._lastReceivedFramePosition += (0, Codecs_1.sizeOfFrame)(frame);
      };
      FrameStore2.prototype.dropTo = function(lastReceivedPosition) {
        var bytesToDrop = lastReceivedPosition - this._firstAvailableFramePosition;
        while (bytesToDrop > 0 && this.storedFrames.length > 0) {
          var storedFrame = this.storedFrames.shift();
          bytesToDrop -= (0, Codecs_1.sizeOfFrame)(storedFrame);
        }
        if (bytesToDrop !== 0) {
          throw new _1.RSocketError(_1.ErrorCodes.CONNECTION_ERROR, "State inconsistency. Expected bytes to drop ".concat(lastReceivedPosition - this._firstAvailableFramePosition, " but actual ").concat(bytesToDrop));
        }
        this._firstAvailableFramePosition = lastReceivedPosition;
      };
      FrameStore2.prototype.drain = function(consumer) {
        var e_1, _a;
        try {
          for (var _b = __values2(this.storedFrames), _c = _b.next(); !_c.done; _c = _b.next()) {
            var frame = _c.value;
            consumer(frame);
          }
        } catch (e_1_1) {
          e_1 = { error: e_1_1 };
        } finally {
          try {
            if (_c && !_c.done && (_a = _b.return)) _a.call(_b);
          } finally {
            if (e_1) throw e_1.error;
          }
        }
      };
      return FrameStore2;
    }()
  );
  Resume.FrameStore = FrameStore;
  return Resume;
}
var hasRequiredRSocketConnector;
function requireRSocketConnector() {
  if (hasRequiredRSocketConnector) return RSocketConnector;
  hasRequiredRSocketConnector = 1;
  var __awaiter = commonjsGlobal && commonjsGlobal.__awaiter || function(thisArg, _arguments, P, generator) {
    function adopt(value) {
      return value instanceof P ? value : new P(function(resolve) {
        resolve(value);
      });
    }
    return new (P || (P = Promise))(function(resolve, reject) {
      function fulfilled(value) {
        try {
          step(generator.next(value));
        } catch (e) {
          reject(e);
        }
      }
      function rejected(value) {
        try {
          step(generator["throw"](value));
        } catch (e) {
          reject(e);
        }
      }
      function step(result) {
        result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected);
      }
      step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
  };
  var __generator2 = commonjsGlobal && commonjsGlobal.__generator || function(thisArg, body) {
    var _ = { label: 0, sent: function() {
      if (t[0] & 1) throw t[1];
      return t[1];
    }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() {
      return this;
    }), g;
    function verb(n) {
      return function(v) {
        return step([n, v]);
      };
    }
    function step(op) {
      if (f) throw new TypeError("Generator is already executing.");
      while (_) try {
        if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
        if (y = 0, t) op = [op[0] & 2, t.value];
        switch (op[0]) {
          case 0:
          case 1:
            t = op;
            break;
          case 4:
            _.label++;
            return { value: op[1], done: false };
          case 5:
            _.label++;
            y = op[1];
            op = [0];
            continue;
          case 7:
            op = _.ops.pop();
            _.trys.pop();
            continue;
          default:
            if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) {
              _ = 0;
              continue;
            }
            if (op[0] === 3 && (!t || op[1] > t[0] && op[1] < t[3])) {
              _.label = op[1];
              break;
            }
            if (op[0] === 6 && _.label < t[1]) {
              _.label = t[1];
              t = op;
              break;
            }
            if (t && _.label < t[2]) {
              _.label = t[2];
              _.ops.push(op);
              break;
            }
            if (t[2]) _.ops.pop();
            _.trys.pop();
            continue;
        }
        op = body.call(thisArg, _);
      } catch (e) {
        op = [6, e];
        y = 0;
      } finally {
        f = t = 0;
      }
      if (op[0] & 5) throw op[1];
      return { value: op[0] ? op[1] : void 0, done: true };
    }
  };
  Object.defineProperty(RSocketConnector, "__esModule", { value: true });
  RSocketConnector.RSocketConnector = void 0;
  var ClientServerMultiplexerDemultiplexer_1 = requireClientServerMultiplexerDemultiplexer();
  var Frames_12 = Frames;
  var RSocketSupport_1 = RSocketSupport;
  var Resume_1 = requireResume();
  var RSocketConnector$1 = (
    /** @class */
    function() {
      function RSocketConnector2(config) {
        this.config = config;
      }
      RSocketConnector2.prototype.connect = function() {
        var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q, _r, _s, _t, _u, _v;
        return __awaiter(this, void 0, void 0, function() {
          var config, setupFrame, connection, keepAliveSender, keepAliveHandler, leaseHandler, responder, connectionFrameHandler, streamsHandler;
          var _this = this;
          return __generator2(this, function(_w) {
            switch (_w.label) {
              case 0:
                config = this.config;
                setupFrame = {
                  type: Frames_12.FrameTypes.SETUP,
                  dataMimeType: (_b = (_a = config.setup) === null || _a === void 0 ? void 0 : _a.dataMimeType) !== null && _b !== void 0 ? _b : "application/octet-stream",
                  metadataMimeType: (_d = (_c = config.setup) === null || _c === void 0 ? void 0 : _c.metadataMimeType) !== null && _d !== void 0 ? _d : "application/octet-stream",
                  keepAlive: (_f = (_e = config.setup) === null || _e === void 0 ? void 0 : _e.keepAlive) !== null && _f !== void 0 ? _f : 6e4,
                  lifetime: (_h = (_g = config.setup) === null || _g === void 0 ? void 0 : _g.lifetime) !== null && _h !== void 0 ? _h : 3e5,
                  metadata: (_k = (_j = config.setup) === null || _j === void 0 ? void 0 : _j.payload) === null || _k === void 0 ? void 0 : _k.metadata,
                  data: (_m = (_l = config.setup) === null || _l === void 0 ? void 0 : _l.payload) === null || _m === void 0 ? void 0 : _m.data,
                  resumeToken: (_p = (_o = config.resume) === null || _o === void 0 ? void 0 : _o.tokenGenerator()) !== null && _p !== void 0 ? _p : null,
                  streamId: 0,
                  majorVersion: 1,
                  minorVersion: 0,
                  flags: (((_r = (_q = config.setup) === null || _q === void 0 ? void 0 : _q.payload) === null || _r === void 0 ? void 0 : _r.metadata) ? Frames_12.Flags.METADATA : Frames_12.Flags.NONE) | (config.lease ? Frames_12.Flags.LEASE : Frames_12.Flags.NONE) | (config.resume ? Frames_12.Flags.RESUME_ENABLE : Frames_12.Flags.NONE)
                };
                return [4, config.transport.connect(function(outbound) {
                  return config.resume ? new ClientServerMultiplexerDemultiplexer_1.ResumableClientServerInputMultiplexerDemultiplexer(
                    ClientServerMultiplexerDemultiplexer_1.StreamIdGenerator.create(-1),
                    outbound,
                    outbound,
                    new Resume_1.FrameStore(),
                    // TODO: add size control
                    setupFrame.resumeToken.toString(),
                    function(self2, frameStore) {
                      return __awaiter(_this, void 0, void 0, function() {
                        var multiplexerDemultiplexerProvider, reconnectionAttempts, reconnector;
                        return __generator2(this, function(_a2) {
                          switch (_a2.label) {
                            case 0:
                              multiplexerDemultiplexerProvider = function(outbound2) {
                                outbound2.send({
                                  type: Frames_12.FrameTypes.RESUME,
                                  streamId: 0,
                                  flags: Frames_12.Flags.NONE,
                                  clientPosition: frameStore.firstAvailableFramePosition,
                                  serverPosition: frameStore.lastReceivedFramePosition,
                                  majorVersion: setupFrame.minorVersion,
                                  minorVersion: setupFrame.majorVersion,
                                  resumeToken: setupFrame.resumeToken
                                });
                                return new ClientServerMultiplexerDemultiplexer_1.ResumeOkAwaitingResumableClientServerInputMultiplexerDemultiplexer(outbound2, outbound2, self2);
                              };
                              reconnectionAttempts = -1;
                              reconnector = function() {
                                reconnectionAttempts++;
                                return config.resume.reconnectFunction(reconnectionAttempts).then(function() {
                                  return config.transport.connect(multiplexerDemultiplexerProvider).catch(reconnector);
                                });
                              };
                              return [4, reconnector()];
                            case 1:
                              _a2.sent();
                              return [
                                2
                                /*return*/
                              ];
                          }
                        });
                      });
                    }
                  ) : new ClientServerMultiplexerDemultiplexer_1.ClientServerInputMultiplexerDemultiplexer(ClientServerMultiplexerDemultiplexer_1.StreamIdGenerator.create(-1), outbound, outbound);
                })];
              case 1:
                connection = _w.sent();
                keepAliveSender = new RSocketSupport_1.KeepAliveSender(connection.multiplexerDemultiplexer.connectionOutbound, setupFrame.keepAlive);
                keepAliveHandler = new RSocketSupport_1.KeepAliveHandler(connection, setupFrame.lifetime);
                leaseHandler = config.lease ? new RSocketSupport_1.LeaseHandler((_s = config.lease.maxPendingRequests) !== null && _s !== void 0 ? _s : 256, connection.multiplexerDemultiplexer) : void 0;
                responder = (_t = config.responder) !== null && _t !== void 0 ? _t : {};
                connectionFrameHandler = new RSocketSupport_1.DefaultConnectionFrameHandler(connection, keepAliveHandler, keepAliveSender, leaseHandler, responder);
                streamsHandler = new RSocketSupport_1.DefaultStreamRequestHandler(responder, 0);
                connection.onClose(function(e) {
                  keepAliveSender.close();
                  keepAliveHandler.close();
                  connectionFrameHandler.close(e);
                });
                connection.multiplexerDemultiplexer.connectionInbound(connectionFrameHandler);
                connection.multiplexerDemultiplexer.handleRequestStream(streamsHandler);
                connection.multiplexerDemultiplexer.connectionOutbound.send(setupFrame);
                keepAliveHandler.start();
                keepAliveSender.start();
                return [2, new RSocketSupport_1.RSocketRequester(connection, (_v = (_u = config.fragmentation) === null || _u === void 0 ? void 0 : _u.maxOutboundFragmentSize) !== null && _v !== void 0 ? _v : 0, leaseHandler)];
            }
          });
        });
      };
      return RSocketConnector2;
    }()
  );
  RSocketConnector.RSocketConnector = RSocketConnector$1;
  return RSocketConnector;
}
var RSocketServer = {};
var hasRequiredRSocketServer;
function requireRSocketServer() {
  if (hasRequiredRSocketServer) return RSocketServer;
  hasRequiredRSocketServer = 1;
  var __awaiter = commonjsGlobal && commonjsGlobal.__awaiter || function(thisArg, _arguments, P, generator) {
    function adopt(value) {
      return value instanceof P ? value : new P(function(resolve) {
        resolve(value);
      });
    }
    return new (P || (P = Promise))(function(resolve, reject) {
      function fulfilled(value) {
        try {
          step(generator.next(value));
        } catch (e) {
          reject(e);
        }
      }
      function rejected(value) {
        try {
          step(generator["throw"](value));
        } catch (e) {
          reject(e);
        }
      }
      function step(result) {
        result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected);
      }
      step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
  };
  var __generator2 = commonjsGlobal && commonjsGlobal.__generator || function(thisArg, body) {
    var _ = { label: 0, sent: function() {
      if (t[0] & 1) throw t[1];
      return t[1];
    }, trys: [], ops: [] }, f, y, t, g;
    return g = { next: verb(0), "throw": verb(1), "return": verb(2) }, typeof Symbol === "function" && (g[Symbol.iterator] = function() {
      return this;
    }), g;
    function verb(n) {
      return function(v) {
        return step([n, v]);
      };
    }
    function step(op) {
      if (f) throw new TypeError("Generator is already executing.");
      while (_) try {
        if (f = 1, y && (t = op[0] & 2 ? y["return"] : op[0] ? y["throw"] || ((t = y["return"]) && t.call(y), 0) : y.next) && !(t = t.call(y, op[1])).done) return t;
        if (y = 0, t) op = [op[0] & 2, t.value];
        switch (op[0]) {
          case 0:
          case 1:
            t = op;
            break;
          case 4:
            _.label++;
            return { value: op[1], done: false };
          case 5:
            _.label++;
            y = op[1];
            op = [0];
            continue;
          case 7:
            op = _.ops.pop();
            _.trys.pop();
            continue;
          default:
            if (!(t = _.trys, t = t.length > 0 && t[t.length - 1]) && (op[0] === 6 || op[0] === 2)) {
              _ = 0;
              continue;
            }
            if (op[0] === 3 && (!t || op[1] > t[0] && op[1] < t[3])) {
              _.label = op[1];
              break;
            }
            if (op[0] === 6 && _.label < t[1]) {
              _.label = t[1];
              t = op;
              break;
            }
            if (t && _.label < t[2]) {
              _.label = t[2];
              _.ops.push(op);
              break;
            }
            if (t[2]) _.ops.pop();
            _.trys.pop();
            continue;
        }
        op = body.call(thisArg, _);
      } catch (e) {
        op = [6, e];
        y = 0;
      } finally {
        f = t = 0;
      }
      if (op[0] & 5) throw op[1];
      return { value: op[0] ? op[1] : void 0, done: true };
    }
  };
  Object.defineProperty(RSocketServer, "__esModule", { value: true });
  RSocketServer.RSocketServer = void 0;
  var ClientServerMultiplexerDemultiplexer_1 = requireClientServerMultiplexerDemultiplexer();
  var Errors_12 = Errors;
  var Frames_12 = Frames;
  var RSocketSupport_1 = RSocketSupport;
  var Resume_1 = requireResume();
  var RSocketServer$1 = (
    /** @class */
    function() {
      function RSocketServer2(config) {
        var _a, _b;
        this.acceptor = config.acceptor;
        this.transport = config.transport;
        this.lease = config.lease;
        this.serverSideKeepAlive = config.serverSideKeepAlive;
        this.sessionStore = config.resume ? {} : void 0;
        this.sessionTimeout = (_b = (_a = config.resume) === null || _a === void 0 ? void 0 : _a.sessionTimeout) !== null && _b !== void 0 ? _b : void 0;
      }
      RSocketServer2.prototype.bind = function() {
        return __awaiter(this, void 0, void 0, function() {
          var _this = this;
          return __generator2(this, function(_a) {
            switch (_a.label) {
              case 0:
                return [4, this.transport.bind(function(frame, connection) {
                  return __awaiter(_this, void 0, void 0, function() {
                    var _a2, error, error, leaseHandler, requester, responder, keepAliveHandler_1, keepAliveSender_1, connectionFrameHandler_1, streamsHandler, e_1;
                    var _b, _c, _d, _e;
                    return __generator2(this, function(_f) {
                      switch (_f.label) {
                        case 0:
                          _a2 = frame.type;
                          switch (_a2) {
                            case Frames_12.FrameTypes.SETUP:
                              return [3, 1];
                            case Frames_12.FrameTypes.RESUME:
                              return [3, 5];
                          }
                          return [3, 6];
                        case 1:
                          _f.trys.push([1, 3, , 4]);
                          if (this.lease && !Frames_12.Flags.hasLease(frame.flags)) {
                            error = new Errors_12.RSocketError(Errors_12.ErrorCodes.REJECTED_SETUP, "Lease has to be enabled");
                            connection.multiplexerDemultiplexer.connectionOutbound.send({
                              type: Frames_12.FrameTypes.ERROR,
                              streamId: 0,
                              flags: Frames_12.Flags.NONE,
                              code: error.code,
                              message: error.message
                            });
                            connection.close(error);
                            return [
                              2
                              /*return*/
                            ];
                          }
                          if (Frames_12.Flags.hasLease(frame.flags) && !this.lease) {
                            error = new Errors_12.RSocketError(Errors_12.ErrorCodes.REJECTED_SETUP, "Lease has to be disabled");
                            connection.multiplexerDemultiplexer.connectionOutbound.send({
                              type: Frames_12.FrameTypes.ERROR,
                              streamId: 0,
                              flags: Frames_12.Flags.NONE,
                              code: error.code,
                              message: error.message
                            });
                            connection.close(error);
                            return [
                              2
                              /*return*/
                            ];
                          }
                          leaseHandler = Frames_12.Flags.hasLease(frame.flags) ? new RSocketSupport_1.LeaseHandler((_b = this.lease.maxPendingRequests) !== null && _b !== void 0 ? _b : 256, connection.multiplexerDemultiplexer) : void 0;
                          requester = new RSocketSupport_1.RSocketRequester(connection, (_d = (_c = this.fragmentation) === null || _c === void 0 ? void 0 : _c.maxOutboundFragmentSize) !== null && _d !== void 0 ? _d : 0, leaseHandler);
                          return [4, this.acceptor.accept({
                            data: frame.data,
                            dataMimeType: frame.dataMimeType,
                            metadata: frame.metadata,
                            metadataMimeType: frame.metadataMimeType,
                            flags: frame.flags,
                            keepAliveMaxLifetime: frame.lifetime,
                            keepAliveInterval: frame.keepAlive,
                            resumeToken: frame.resumeToken
                          }, requester)];
                        case 2:
                          responder = _f.sent();
                          keepAliveHandler_1 = new RSocketSupport_1.KeepAliveHandler(connection, frame.lifetime);
                          keepAliveSender_1 = this.serverSideKeepAlive ? new RSocketSupport_1.KeepAliveSender(connection.multiplexerDemultiplexer.connectionOutbound, frame.keepAlive) : void 0;
                          connectionFrameHandler_1 = new RSocketSupport_1.DefaultConnectionFrameHandler(connection, keepAliveHandler_1, keepAliveSender_1, leaseHandler, responder);
                          streamsHandler = new RSocketSupport_1.DefaultStreamRequestHandler(responder, 0);
                          connection.onClose(function(e) {
                            keepAliveSender_1 === null || keepAliveSender_1 === void 0 ? void 0 : keepAliveSender_1.close();
                            keepAliveHandler_1.close();
                            connectionFrameHandler_1.close(e);
                          });
                          connection.multiplexerDemultiplexer.connectionInbound(connectionFrameHandler_1);
                          connection.multiplexerDemultiplexer.handleRequestStream(streamsHandler);
                          keepAliveHandler_1.start();
                          keepAliveSender_1 === null || keepAliveSender_1 === void 0 ? void 0 : keepAliveSender_1.start();
                          return [3, 4];
                        case 3:
                          e_1 = _f.sent();
                          connection.multiplexerDemultiplexer.connectionOutbound.send({
                            type: Frames_12.FrameTypes.ERROR,
                            streamId: 0,
                            code: Errors_12.ErrorCodes.REJECTED_SETUP,
                            message: (_e = e_1.message) !== null && _e !== void 0 ? _e : "",
                            flags: Frames_12.Flags.NONE
                          });
                          connection.close(e_1 instanceof Errors_12.RSocketError ? e_1 : new Errors_12.RSocketError(Errors_12.ErrorCodes.REJECTED_SETUP, e_1.message));
                          return [3, 4];
                        case 4:
                          return [
                            2
                            /*return*/
                          ];
                        case 5: {
                          return [
                            2
                            /*return*/
                          ];
                        }
                        case 6:
                          {
                            connection.multiplexerDemultiplexer.connectionOutbound.send({
                              type: Frames_12.FrameTypes.ERROR,
                              streamId: 0,
                              code: Errors_12.ErrorCodes.UNSUPPORTED_SETUP,
                              message: "Unsupported setup",
                              flags: Frames_12.Flags.NONE
                            });
                            connection.close(new Errors_12.RSocketError(Errors_12.ErrorCodes.UNSUPPORTED_SETUP));
                          }
                          _f.label = 7;
                        case 7:
                          return [
                            2
                            /*return*/
                          ];
                      }
                    });
                  });
                }, function(frame, outbound) {
                  if (frame.type === Frames_12.FrameTypes.RESUME) {
                    if (_this.sessionStore) {
                      var multiplexerDemultiplexer = _this.sessionStore[frame.resumeToken.toString()];
                      if (!multiplexerDemultiplexer) {
                        outbound.send({
                          type: Frames_12.FrameTypes.ERROR,
                          streamId: 0,
                          code: Errors_12.ErrorCodes.REJECTED_RESUME,
                          message: "No session found for the given resume token",
                          flags: Frames_12.Flags.NONE
                        });
                        outbound.close();
                        return;
                      }
                      multiplexerDemultiplexer.resume(frame, outbound, outbound);
                      return multiplexerDemultiplexer;
                    }
                    outbound.send({
                      type: Frames_12.FrameTypes.ERROR,
                      streamId: 0,
                      code: Errors_12.ErrorCodes.REJECTED_RESUME,
                      message: "Resume is not enabled",
                      flags: Frames_12.Flags.NONE
                    });
                    outbound.close();
                    return;
                  } else if (frame.type === Frames_12.FrameTypes.SETUP) {
                    if (Frames_12.Flags.hasResume(frame.flags)) {
                      if (!_this.sessionStore) {
                        var error = new Errors_12.RSocketError(Errors_12.ErrorCodes.REJECTED_SETUP, "No resume support");
                        outbound.send({
                          type: Frames_12.FrameTypes.ERROR,
                          streamId: 0,
                          flags: Frames_12.Flags.NONE,
                          code: error.code,
                          message: error.message
                        });
                        outbound.close(error);
                        return;
                      }
                      var multiplexerDumiltiplexer = new ClientServerMultiplexerDemultiplexer_1.ResumableClientServerInputMultiplexerDemultiplexer(
                        ClientServerMultiplexerDemultiplexer_1.StreamIdGenerator.create(0),
                        outbound,
                        outbound,
                        new Resume_1.FrameStore(),
                        // TODO: add size parameter
                        frame.resumeToken.toString(),
                        _this.sessionStore,
                        _this.sessionTimeout
                      );
                      _this.sessionStore[frame.resumeToken.toString()] = multiplexerDumiltiplexer;
                      return multiplexerDumiltiplexer;
                    }
                  }
                  return new ClientServerMultiplexerDemultiplexer_1.ClientServerInputMultiplexerDemultiplexer(ClientServerMultiplexerDemultiplexer_1.StreamIdGenerator.create(0), outbound, outbound);
                })];
              case 1:
                return [2, _a.sent()];
            }
          });
        });
      };
      return RSocketServer2;
    }()
  );
  RSocketServer.RSocketServer = RSocketServer$1;
  return RSocketServer;
}
var Transport = {};
Object.defineProperty(Transport, "__esModule", { value: true });
var hasRequiredDist;
function requireDist() {
  if (hasRequiredDist) return dist$1;
  hasRequiredDist = 1;
  (function(exports$1) {
    var __createBinding2 = commonjsGlobal && commonjsGlobal.__createBinding || (Object.create ? function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      Object.defineProperty(o, k2, { enumerable: true, get: function() {
        return m[k];
      } });
    } : function(o, m, k, k2) {
      if (k2 === void 0) k2 = k;
      o[k2] = m[k];
    });
    var __exportStar = commonjsGlobal && commonjsGlobal.__exportStar || function(m, exports$12) {
      for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports$12, p)) __createBinding2(exports$12, m, p);
    };
    Object.defineProperty(exports$1, "__esModule", { value: true });
    __exportStar(Codecs, exports$1);
    __exportStar(Common, exports$1);
    __exportStar(Deferred$1, exports$1);
    __exportStar(Errors, exports$1);
    __exportStar(Frames, exports$1);
    __exportStar(RSocket, exports$1);
    __exportStar(requireRSocketConnector(), exports$1);
    __exportStar(requireRSocketServer(), exports$1);
    __exportStar(Transport, exports$1);
  })(dist$1);
  return dist$1;
}
var distExports = requireDist();
var dist = {};
var WebsocketClientTransport$1 = {};
var WebsocketDuplexConnection$1 = {};
var __extends = commonjsGlobal && commonjsGlobal.__extends || /* @__PURE__ */ function() {
  var extendStatics = function(d, b) {
    extendStatics = Object.setPrototypeOf || { __proto__: [] } instanceof Array && function(d2, b2) {
      d2.__proto__ = b2;
    } || function(d2, b2) {
      for (var p in b2) if (Object.prototype.hasOwnProperty.call(b2, p)) d2[p] = b2[p];
    };
    return extendStatics(d, b);
  };
  return function(d, b) {
    if (typeof b !== "function" && b !== null)
      throw new TypeError("Class extends value " + String(b) + " is not a constructor or null");
    extendStatics(d, b);
    function __() {
      this.constructor = d;
    }
    d.prototype = b === null ? Object.create(b) : (__.prototype = b.prototype, new __());
  };
}();
Object.defineProperty(WebsocketDuplexConnection$1, "__esModule", { value: true });
WebsocketDuplexConnection$1.WebsocketDuplexConnection = void 0;
var rsocket_core_1$1 = requireDist();
var WebsocketDuplexConnection = (
  /** @class */
  function(_super) {
    __extends(WebsocketDuplexConnection2, _super);
    function WebsocketDuplexConnection2(websocket, deserializer, multiplexerDemultiplexerFactory) {
      var _this = _super.call(this) || this;
      _this.websocket = websocket;
      _this.deserializer = deserializer;
      _this.handleClosed = function(e) {
        _this.close(new Error(e.reason || "WebsocketDuplexConnection: Socket closed unexpectedly."));
      };
      _this.handleError = function(e) {
        _this.close(e.error);
      };
      _this.handleMessage = function(message) {
        try {
          var buffer = Buffer.from(message.data);
          var frame = _this.deserializer.deserializeFrame(buffer);
          _this.multiplexerDemultiplexer.handle(frame);
        } catch (error) {
          _this.close(error);
        }
      };
      websocket.addEventListener("close", _this.handleClosed);
      websocket.addEventListener("error", _this.handleError);
      websocket.addEventListener("message", _this.handleMessage);
      _this.multiplexerDemultiplexer = multiplexerDemultiplexerFactory(_this);
      return _this;
    }
    Object.defineProperty(WebsocketDuplexConnection2.prototype, "availability", {
      get: function() {
        return this.done ? 0 : 1;
      },
      enumerable: false,
      configurable: true
    });
    WebsocketDuplexConnection2.prototype.close = function(error) {
      if (this.done) {
        _super.prototype.close.call(this, error);
        return;
      }
      this.websocket.removeEventListener("close", this.handleClosed);
      this.websocket.removeEventListener("error", this.handleError);
      this.websocket.removeEventListener("message", this.handleMessage);
      this.websocket.close();
      delete this.websocket;
      _super.prototype.close.call(this, error);
    };
    WebsocketDuplexConnection2.prototype.send = function(frame) {
      if (this.done) {
        return;
      }
      var buffer = (0, rsocket_core_1$1.serializeFrame)(frame);
      this.websocket.send(buffer);
    };
    return WebsocketDuplexConnection2;
  }(rsocket_core_1$1.Deferred)
);
WebsocketDuplexConnection$1.WebsocketDuplexConnection = WebsocketDuplexConnection;
Object.defineProperty(WebsocketClientTransport$1, "__esModule", { value: true });
WebsocketClientTransport$1.WebsocketClientTransport = void 0;
var rsocket_core_1 = requireDist();
var WebsocketDuplexConnection_1 = WebsocketDuplexConnection$1;
var WebsocketClientTransport = (
  /** @class */
  function() {
    function WebsocketClientTransport2(options) {
      var _a;
      this.url = options.url;
      this.factory = (_a = options.wsCreator) !== null && _a !== void 0 ? _a : function(url) {
        return new WebSocket(url);
      };
    }
    WebsocketClientTransport2.prototype.connect = function(multiplexerDemultiplexerFactory) {
      var _this = this;
      return new Promise(function(resolve, reject) {
        var websocket = _this.factory(_this.url);
        websocket.binaryType = "arraybuffer";
        var openListener = function() {
          websocket.removeEventListener("open", openListener);
          websocket.removeEventListener("error", errorListener);
          resolve(new WebsocketDuplexConnection_1.WebsocketDuplexConnection(websocket, new rsocket_core_1.Deserializer(), multiplexerDemultiplexerFactory));
        };
        var errorListener = function(ev) {
          websocket.removeEventListener("open", openListener);
          websocket.removeEventListener("error", errorListener);
          reject(ev.error);
        };
        websocket.addEventListener("open", openListener);
        websocket.addEventListener("error", errorListener);
      });
    };
    return WebsocketClientTransport2;
  }()
);
WebsocketClientTransport$1.WebsocketClientTransport = WebsocketClientTransport;
(function(exports$1) {
  var __createBinding2 = commonjsGlobal && commonjsGlobal.__createBinding || (Object.create ? function(o, m, k, k2) {
    if (k2 === void 0) k2 = k;
    Object.defineProperty(o, k2, { enumerable: true, get: function() {
      return m[k];
    } });
  } : function(o, m, k, k2) {
    if (k2 === void 0) k2 = k;
    o[k2] = m[k];
  });
  var __exportStar = commonjsGlobal && commonjsGlobal.__exportStar || function(m, exports$12) {
    for (var p in m) if (p !== "default" && !Object.prototype.hasOwnProperty.call(exports$12, p)) __createBinding2(exports$12, m, p);
  };
  Object.defineProperty(exports$1, "__esModule", { value: true });
  __exportStar(WebsocketClientTransport$1, exports$1);
})(dist);
export {
  getAugmentedNamespace as a,
  dist as b,
  commonjsGlobal as c,
  distExports as d,
  getDefaultExportFromCjs as g
};
//# sourceMappingURL=rsocket.js.map
