import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    path: '/mcp',
    name: 'AgentMcpRoutes',
    meta: { title: 'MCP', hideInMenu: true },
    children: [
      {
        path: 'tool',
        component: () => import('#/views/mcp/tool/index.vue'),
        name: 'McpTool',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'MCP Tools',
          activePath: '/mcp-server',
        },
      },
    ],
  },
  {
    path: '/knowledge',
    name: 'AgentKnowledgeRoutes',
    meta: { title: 'Knowledge', hideInMenu: true },
    children: [
      {
        path: 'document',
        component: () => import('#/views/knowledge/document/index.vue'),
        name: 'KnowledgeDocument',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Knowledge Documents',
          activePath: '/knowledge-base',
        },
      },
      {
        path: 'chunk',
        component: () => import('#/views/knowledge/chunk/index.vue'),
        name: 'KnowledgeChunk',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Knowledge Chunks',
          activePath: '/knowledge-base',
        },
      },
      {
        path: 'embedding',
        component: () => import('#/views/knowledge/embedding/index.vue'),
        name: 'KnowledgeEmbedding',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Knowledge Embeddings',
          activePath: '/knowledge-base',
        },
      },
    ],
  },
  {
    path: '/agent-skill-routes',
    name: 'AgentSkillRoutes',
    meta: { title: 'Skill', hideInMenu: true },
    children: [
      {
        path: '/skill/version',
        component: () => import('#/views/skill/version/index.vue'),
        name: 'SkillVersion',
        meta: {
          noCache: true,
          hidden: true,
          canTo: true,
          title: 'Skill Versions',
          activePath: '/skill',
        },
      },
    ],
  },
];

export default routes;
