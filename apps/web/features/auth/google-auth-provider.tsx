"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { getGoogleAuthStatus } from "./google-auth-status";

type State = "loading" | "configured" | "unconfigured" | "error";
const GoogleAuthContext = createContext<{state:State;retry():void}>({state:"loading",retry:()=>{}});

export function GoogleAuthProvider({children}:{children:ReactNode}) {
  const [state,setState] = useState<State>("loading");
  const [attempt,setAttempt] = useState(0);
  useEffect(()=>{
    let cancelled = false;
    getGoogleAuthStatus().then(configured=>{
      if (!cancelled) setState(configured ? "configured" : "unconfigured");
    }).catch(()=>{if (!cancelled) setState("error");});
    return ()=>{cancelled=true;};
  },[attempt]);
  function retry() { setState("loading");setAttempt(value=>value+1); }
  return <GoogleAuthContext.Provider value={{state,retry}}>{children}</GoogleAuthContext.Provider>;
}

export function useGoogleAuthStatus() { return useContext(GoogleAuthContext); }
