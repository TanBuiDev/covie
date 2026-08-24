"use client";
import { useEffect, useState } from "react";
import { loadAdminSettingDefinitions, updateGlobalAdminSetting } from "./admin-api";
import { SettingsEditor, type AdminSettingDefinition } from "./settings-editor";
export function AdminSettingsScreen() { const [definitions,setDefinitions]=useState<AdminSettingDefinition[]>([]); const [error,setError]=useState<string>(); const [etags,setEtags]=useState<Record<string,string>>({}); useEffect(()=>{loadAdminSettingDefinitions().then(setDefinitions).catch(e=>setError(e instanceof Error?e.message:"Unable to load settings"));},[]); if(error)return <p role="alert">{error}</p>; return <SettingsEditor definitions={definitions} onSave={async(key,value)=>{const etag=await updateGlobalAdminSetting(key,value,etags[key]); if(etag)setEtags(current=>({...current,[key]:etag}));}} />; }
