"""Firebase authentication middleware for FastAPI."""
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from typing import Optional
import os

try:
    import firebase_admin
    from firebase_admin import credentials, auth
    FIREBASE_AVAILABLE = True
except ImportError:
    FIREBASE_AVAILABLE = False
    print("[Auth] Firebase Admin SDK not installed. Authentication disabled.")

security = HTTPBearer(auto_error=False)

# Initialize Firebase Admin SDK
_firebase_initialized = False

def initialize_firebase():
    """Initialize Firebase Admin SDK if not already initialized."""
    global _firebase_initialized
    
    if not FIREBASE_AVAILABLE:
        return False
    
    if _firebase_initialized:
        return True
    
    try:
        import json
        # Option 1: Full JSON content in env var (for cloud deployment like Render)
        sdk_json = os.getenv("FIREBASE_ADMIN_SDK_JSON")
        if sdk_json:
            cred = credentials.Certificate(json.loads(sdk_json))
            firebase_admin.initialize_app(cred)
            _firebase_initialized = True
            print("[Auth] Firebase Admin SDK initialized from env var")
            return True

        # Option 2: File path (for local development)
        sdk_path = os.getenv("FIREBASE_ADMIN_SDK_PATH", "firebase-admin-sdk.json")
        
        if not os.path.exists(sdk_path):
            print(f"[Auth] Firebase Admin SDK file not found at: {sdk_path}")
            return False
        
        cred = credentials.Certificate(sdk_path)
        firebase_admin.initialize_app(cred)
        _firebase_initialized = True
        print("[Auth] Firebase Admin SDK initialized successfully")
        return True
    except Exception as e:
        print(f"[Auth] Failed to initialize Firebase: {e}")
        return False


async def get_current_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security)
) -> str:
    """
    Verify Firebase ID token and return user ID.
    
    If Firebase is not configured, returns a default user ID for development.
    """
    # Development fallback
    if not FIREBASE_AVAILABLE or not initialize_firebase():
        print("[Auth] Using default user (Firebase not configured)")
        return "default_user"
    
    if not credentials:
        # Allow unauthenticated access in development
        print("[Auth] No credentials provided, using default user")
        return "default_user"
    
    try:
        # Verify the Firebase ID token
        decoded_token = auth.verify_id_token(credentials.credentials)
        user_id = decoded_token['uid']
        print(f"[Auth] Authenticated user: {user_id}")
        return user_id
    except auth.InvalidIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid authentication token",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except auth.ExpiredIdTokenError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication token has expired",
            headers={"WWW-Authenticate": "Bearer"},
        )
    except Exception as e:
        print(f"[Auth] Token verification failed: {e}")
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=f"Authentication failed: {str(e)}",
            headers={"WWW-Authenticate": "Bearer"},
        )


async def get_optional_user(
    credentials: Optional[HTTPAuthorizationCredentials] = Depends(security)
) -> Optional[str]:
    """
    Get user ID if authenticated, otherwise return None.
    Useful for endpoints that work for both authenticated and anonymous users.
    """
    if not credentials:
        return None
    
    try:
        return await get_current_user(credentials)
    except HTTPException:
        return None
