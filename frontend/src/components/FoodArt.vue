<script setup lang="ts">
withDefaults(defineProps<{ kind?: string }>(), { kind: 'chicken' })
</script>
<template>
  <svg viewBox="0 0 280 200" class="food-art" role="img" :aria-label="`${kind} 菜品示意插画`">
    <rect width="280" height="200" :fill="kind === 'salad' ? '#e9eedf' : kind === 'fish' ? '#e7edeb' : '#f0e7d8'" />
    <path d="M235 0v200M250 0v200" stroke="#fff" stroke-width="1" opacity=".5" />
    <ellipse cx="141" cy="109" rx="96" ry="68" fill="#c8b99e" opacity=".2" />
    <ellipse cx="140" cy="98" rx="95" ry="69" fill="#fbfaf4" />
    <ellipse cx="140" cy="98" rx="80" ry="57" fill="#fff" stroke="#e4e1d5" stroke-width="1" />
    <g v-if="kind === 'chicken' || kind === 'beef'">
      <ellipse cx="117" cy="100" rx="46" ry="30" transform="rotate(-17 117 100)" :fill="kind === 'beef' ? '#855439' : '#bb803e'" />
      <path d="m86 89 58 12m-56 1 51 11m-49-32 57 12" stroke="#704728" stroke-width="4" opacity=".5" stroke-linecap="round" />
      <path d="m95 76 46-2-12 8-39 9z" fill="#e6b26a" opacity=".65" />
      <circle cx="174" cy="117" r="16" fill="#71a46c" /><circle cx="194" cy="105" r="13" fill="#88b076" /><circle cx="177" cy="92" r="15" fill="#5a905e" /><path d="m178 111-3 24m15-28-9 15" stroke="#bec985" stroke-width="4" />
      <ellipse cx="163" cy="70" rx="22" ry="10" fill="#f0e8c5" /><path d="m144 69 31-3m-26 8 29-5" stroke="#fff" stroke-width="2" />
    </g>
    <g v-else-if="kind === 'fish'">
      <path d="M76 97q48-48 107-3l22-17-2 38-22-13q-51 39-105-5" fill="#dad1b5" stroke="#bcb497" stroke-width="2" />
      <path d="m99 90 56 17m-49-25 61 14m-39-23 50 13" stroke="#b1a486" stroke-width="2" /><circle cx="84" cy="96" r="3" fill="#515342" />
      <path d="m92 114 89-30m-69 29 55-35" stroke="#54835a" stroke-width="3" /><path d="m141 67 5 50" stroke="#d9bc6b" stroke-width="3" />
      <circle cx="176" cy="124" r="12" fill="#c9d995" /><circle cx="192" cy="119" r="9" fill="#79a472" />
    </g>
    <g v-else-if="kind === 'salad'">
      <g fill="#79a46b"><ellipse cx="102" cy="95" rx="25" ry="17" transform="rotate(-30 102 95)" /><ellipse cx="143" cy="77" rx="25" ry="17" /><ellipse cx="165" cy="113" rx="29" ry="20" /><ellipse cx="114" cy="122" rx="26" ry="16" /></g>
      <g fill="#a6bc78"><ellipse cx="161" cy="88" rx="24" ry="14" transform="rotate(28 161 88)" /><ellipse cx="133" cy="108" rx="24" ry="17" /></g>
      <g fill="#d9815e"><circle cx="97" cy="115" r="10" /><circle cx="155" cy="117" r="10" /><circle cx="167" cy="74" r="9" /></g>
      <g fill="#e5c46a"><circle cx="115" cy="88" r="4" /><circle cx="123" cy="83" r="4" /><circle cx="140" cy="120" r="4" /><circle cx="177" cy="106" r="4" /><circle cx="143" cy="90" r="4" /></g>
    </g>
    <g v-else-if="kind === 'noodle'">
      <ellipse cx="140" cy="98" rx="64" ry="40" fill="#d99d62" /><g fill="none" stroke="#f0d895" stroke-width="5"><path d="M100 85q40-30 70 4t-56 21 53 17" /><path d="M91 98q28-21 76-4t-53 11 40 30" /><path d="M110 72q28 8 48 35t-52 15" /></g><path d="M134 84q6-17 22-8t-7 21q-11 6-15-13" fill="#fff4d4" /><circle cx="147" cy="85" r="7" fill="#e9b856" /><path d="m91 106 20 7m50-9 18 9" stroke="#56835a" stroke-width="5" />
    </g>
    <g v-else>
      <ellipse cx="140" cy="100" rx="60" ry="36" fill="#a66139" /><g fill="#edce9b" stroke="#d6ad70" stroke-width="2"><rect x="100" y="81" width="25" height="22" rx="3" /><rect x="129" y="78" width="25" height="22" rx="3" /><rect x="156" y="89" width="25" height="22" rx="3" /><rect x="115" y="106" width="25" height="22" rx="3" /><rect x="145" y="110" width="25" height="22" rx="3" /></g><path d="m101 93 11-4m30 20 10-5m17-13 9 3" stroke="#588357" stroke-width="3" />
    </g>
    <path d="M17 18v63m6-63v63m-12-63v30q6 14 12 0" stroke="#8c937d" stroke-width="3" fill="none" stroke-linecap="round" />
    <path d="M265 28v76m0-76q-12 17 0 32" stroke="#8c937d" stroke-width="3" fill="none" stroke-linecap="round" />
  </svg>
</template>
