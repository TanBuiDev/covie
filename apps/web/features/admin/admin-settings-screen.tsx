"use client";
import { useEffect, useState } from "react";
import { loadAdminSettingDefinitions } from "./admin-api";
import { SettingsEditor, type AdminSettingDefinition } from "./settings-editor";
export function AdminSettingsScreen() { const [definitions,setDefinitions]=useState<AdminSettingDefinition[]>([]); const [error,setError]=useState<string>(); useEffect(()=>{loadAdminSettingDefinitions().then(setDefinitions).catch(e=>setError(e instanceof Error?e.message:"Unable to load settings"));},[]); if(error)return <p role="alert">{error}</p>; return <SettingsEditor definitions={definitions} />; }
