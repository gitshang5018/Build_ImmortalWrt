package web

import (
	"embed"
	"io/fs"
	"net/http"
	"os"
)

//go:embed dist/*
var distFS embed.FS

// AssetHandler returns an HTTP handler for the WebUI.
// If /www/aerowrt directory exists on the system, it serves directly from disk for live customization.
// Otherwise, it serves from the embedded distFS.
// Cache-Control headers are added to prevent stale browser caching.
func AssetHandler() http.Handler {
	localDir := "/www/aerowrt"
	if info, err := os.Stat(localDir); err == nil && info.IsDir() {
		return noCache(http.FileServer(http.Dir(localDir)))
	}

	sub, err := fs.Sub(distFS, "dist")
	if err != nil {
		panic(err)
	}
	return noCache(http.FileServer(http.FS(sub)))
}

func noCache(h http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Cache-Control", "no-cache, no-store, must-revalidate")
		w.Header().Set("Pragma", "no-cache")
		w.Header().Set("Expires", "0")
		h.ServeHTTP(w, r)
	})
}
