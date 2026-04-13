if (typeof globalThis.File === "undefined") {
  class TestFile extends Blob {
    readonly name: string;
    readonly lastModified: number;

    constructor(fileBits: BlobPart[], fileName: string, options: FilePropertyBag = {}) {
      super(fileBits, options);
      this.name = fileName;
      this.lastModified = options.lastModified ?? Date.now();
    }

    get [Symbol.toStringTag](): string {
      return "File";
    }
  }

  Object.defineProperty(globalThis, "File", {
    value: TestFile,
    configurable: true
  });
}
