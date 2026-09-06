package group

import (
	"math/rand"
	"aerowrt/internal/model"
)

type Manager struct{}

func NewManager() *Manager {
	return &Manager{}
}

func (m *Manager) SelectNode(grp model.OutboundGroup, nodes map[string]model.Node) string {
	if len(grp.Nodes) == 0 {
		return ""
	}

	switch grp.Type {
	case model.GroupTypeSelector:
		if grp.Selected != "" {
			return grp.Selected
		}
		return grp.Nodes[0]

	case model.GroupTypeUrlTest:
		bestID := ""
		minDelay := int64(999999)
		for _, nid := range grp.Nodes {
			n, exists := nodes[nid]
			if exists && n.DelayMs > 0 && n.DelayMs < minDelay {
				minDelay = n.DelayMs
				bestID = nid
			}
		}
		if bestID != "" {
			return bestID
		}
		return grp.Nodes[0]

	case model.GroupTypeFailover:
		for _, nid := range grp.Nodes {
			n, exists := nodes[nid]
			if exists && n.DelayMs > 0 {
				return nid
			}
		}
		return grp.Nodes[0]

	case model.GroupTypeLoadBalance:
		validNodes := make([]string, 0)
		for _, nid := range grp.Nodes {
			n, exists := nodes[nid]
			if exists && n.DelayMs > 0 {
				validNodes = append(validNodes, nid)
			}
		}
		if len(validNodes) > 0 {
			return validNodes[rand.Intn(len(validNodes))]
		}
		return grp.Nodes[0]

	default:
		return grp.Nodes[0]
	}
}
