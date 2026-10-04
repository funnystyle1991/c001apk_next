package com.example.c001apk.ui.applist

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import com.example.c001apk.R
import com.example.c001apk.adapter.HeaderAdapter
import com.example.c001apk.adapter.PlaceHolderAdapter
import com.example.c001apk.databinding.BaseRefreshRecyclerviewBinding
import com.example.c001apk.ui.base.BaseViewFragment
import com.example.c001apk.ui.home.IOnTabClickContainer
import com.example.c001apk.ui.home.IOnTabClickListener
import com.example.c001apk.ui.main.IOnBottomClickContainer
import com.example.c001apk.ui.main.IOnBottomClickListener
import com.example.c001apk.ui.main.MainActivity
import com.example.c001apk.util.setSpaceFooterView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class AppListFragment : BaseViewFragment<AppListViewModel>(), IOnTabClickListener,
    IOnBottomClickListener {

    override val viewModel by viewModels<AppListViewModel>()
    private lateinit var appsAdapter: AppListAdapter
    private val placeHolderAdapter by lazy { PlaceHolderAdapter() }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BaseRefreshRecyclerviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun initAdapter() {
        appsAdapter = AppListAdapter()
        mAdapter = ConcatAdapter(HeaderAdapter(), appsAdapter)
    }

    override fun fetchData() {
        viewModel.getItems(requireContext())
    }

    override fun initView() {
        super.initView()
        binding.vfContainer.setOnDisplayedChildChangedListener {
            binding.recyclerView.setSpaceFooterView(placeHolderAdapter)
        }
    }

    override fun initObserve() {
        super.initObserve()

        viewModel.items.observe(viewLifecycleOwner) {
            appsAdapter.submitList(it)
            binding.swipeRefresh.isRefreshing = false
            if (binding.vfContainer.displayedChild != it.size)
                binding.vfContainer.displayedChild = it.size
        }
    }

    override fun onScrolled(dy: Int) {
        if (dy > 0) {
            (activity as? MainActivity)?.hideNavigationView()
        } else if (dy < 0) {
            (activity as? MainActivity)?.showNavigationView()
        }
    }


    override fun onResume() {
        super.onResume()
        (parentFragment as? IOnTabClickContainer)?.tabController = this
        (activity as? IOnBottomClickContainer)?.controller = this
    }

    override fun onPause() {
        super.onPause()
        (parentFragment as? IOnTabClickContainer)?.tabController = null
        (activity as? IOnBottomClickContainer)?.controller = null
    }

    override fun onReturnTop() {
        onReturnTop(null)
    }

    override fun onReturnTop(isRefresh: Boolean?) {
        returnTopOrRefresh()
    }

}
