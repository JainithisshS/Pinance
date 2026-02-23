"""Supabase client singleton for database operations."""

from typing import Optional
import os
from supabase import create_client, Client

_supabase: Optional[Client] = None


def get_supabase() -> Client:
    """Get or create Supabase client instance.
    
    Requires environment variables:
    - SUPABASE_URL: Your Supabase project URL
    - SUPABASE_SERVICE_KEY: Service role key (for backend operations)
    
    Raises:
        ValueError: If required environment variables are not set
    """
    global _supabase
    
    if _supabase is None:
        url = os.getenv("SUPABASE_URL")
        key = os.getenv("SUPABASE_SERVICE_KEY")
        
        if not url or not key:
            raise ValueError(
                "SUPABASE_URL and SUPABASE_SERVICE_KEY environment variables must be set. "
                "Get these from your Supabase project settings."
            )
        
        _supabase = create_client(url, key)
        print(f"[Supabase] Connected to {url}")
    
    return _supabase
