<template>
  <main class="app-shell">
    <section class="workspace">
      <header class="topbar">
        <div>
          <p class="eyebrow">CampusHub</p>
          <h1>时迁校园资源共享平台</h1>
        </div>
        <span class="status">Vue3 + Vite</span>
      </header>

      <nav class="nav-tabs" aria-label="主导航">
        <RouterLink to="/">首页</RouterLink>
        <RouterLink to="/resources">资源</RouterLink>
        <RouterLink to="/resources/upload">上传</RouterLink>
        <RouterLink to="/profile">个人中心</RouterLink>
      </nav>

      <RouterView />
    </section>
  </main>
</template>
