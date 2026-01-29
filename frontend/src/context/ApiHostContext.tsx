import { createContext, useContext } from "solid-js";
import type { ParentProps } from "solid-js";

// Update this value when the backend host changes.
const API_HOST = "https://aphinityms.com/";

const ApiHostContext = createContext<string>(API_HOST);

export const ApiHostProvider = (props: ParentProps) => (
  <ApiHostContext.Provider value={API_HOST}>
    {props.children}
  </ApiHostContext.Provider>
);

export const useApiHost = () => useContext(ApiHostContext);

export { API_HOST };
